package lib

import scala.collection.concurrent.{TrieMap => ConcurrentMap}

import scala.concurrent.duration._
import scala.util.control.NonFatal

import _root_.play.api.libs.json.Json
import org.slf4j.LoggerFactory
import org.apache.commons.io.FilenameUtils._

import scalaz.syntax.bind._
import scalaz.concurrent.Task
import scalaz.stream._
import scalaz.{\/-, \/, -\/}
import Process._
import io.resource

import com.gu.mediaservice.lib.Processes._
import com.gu.mediaservice.syntax.ProcessSyntax._

class FTPWatcher(host: String, port: Int, user: String, password: String) extends Concurrency {

  val mediaApiKey = Config.mediaApiKey
  val guMediaApiHeader = "X-Gu-Media-Key"

  def run: Task[Unit] =
    uploads
      .pipe(process1.liftL(repeatedFailureThreshold(3)))
      .drainW(moveFailedUploads)
      .to(deleteFile)
      .run

//  def uniquely(c: Channel[Task, FilePath, File]): Process1[FilePath, FilePath] = {
//    def go(acc: Set[FilePath]): Process1[FilePath, FilePath] =
//      await1[FilePath].flatMap { case a =>
//        if (! acc.contains(a))
//          emit(a) fby go(acc + a)
//        else
//          go(acc)
//      }
//    go(Set.empty)
//  }

  val uploadingFiles: ConcurrentMap[FilePath, Unit] = ConcurrentMap.empty


//    def retrieveAndUpload: Channel[Task, FilePath, FailedUpload \/ FilePath] =
////  def retrieveAndUpload: Writer[Task, FailedUpload, FilePath] =
//      retrieveFile.
//        through(uploadImage)
////        concurrently(3)(uploadImage)
////    await1[FilePath].flatMap { case f =>
////      retrieveFile(f).
////        concurrently(3)(uploadImage)
////    }


  /** A process which logs failed uploads on the left hand side, and successful ones on the right */
  def uploads: Writer[Task, FailedUpload, FilePath] =
    whileActive(100.millis)(watchFiles(10))
      .filter { path =>
        uploadingFiles.putIfAbsent(path, ()).isEmpty
      }
      .through(retrieveFile)
      .concurrently(3)(uploadImage)
      .map {
        case v @ \/-(path) => uploadingFiles.remove(path); v
        case v @ -\/(failed) => uploadingFiles.remove(failed.path); v
      }


  def watchFiles(maxPerDir: Int): Process[Task, FilePath] =
    sleepIfEmpty(100.millis)(withClient(listFiles(maxPerDir))).unchunk

  import scalaz.ListT
  import FTPWatcherMetrics._

  def listFiles(maxPerDir: Int)(client: Client): Task[List[FilePath]] =
    (for {
      dir  <- ListT(client.listDirectories(".")).filter(Config.ftpPaths.contains)
      file <- ListT(client.listFiles(dir)).take(maxPerDir)
    } yield concat(dir, file)).toList

  def retrieveFile: Channel[Task, FilePath, File] =
    withClient { client =>
      Task.now { path: FilePath =>
        logger.info(s"START RETRIEVE $path")
        val uploadedBy = path.takeWhile(_ != '/')

        logger.info(s"Retrieving file: $path from: $uploadedBy")
        retrievingImages.increment(List(uploadedByDimension(uploadedBy)))

        client.retrieveFile(path) map {
          logger.info(s"Retrieved file: $path from: $uploadedBy")
          retrievedImages.increment(List(uploadedByDimension(uploadedBy)))
          logger.info(s"OK RETRIEVED $path")
          bytes => File(path, bytes, uploadedBy)
        }
      }
    }

  def deleteFile: Sink[Task, FilePath] =
    withClient(client => Task.now { path: FilePath => client.delete(path) })

  def moveFailedUploads: Sink[Task, FailedUpload] =
    withClient { client =>
      Task.now { case FailedUpload(path) =>
        val destDir = getPath(path) + "failed"
        val destPath = destDir + "/" + getName(path)
        logger.warn(s"$path breached the failure threshold, moving to $destPath")
        client.mkDir(destDir) >> client.rename(path, destPath)
      }
    }

  def repeatedFailureThreshold(threshold: Int): Process1[FailedUpload, FailedUpload] =
    emitEveryNth(threshold)

  def withClient[A](f: Client => Task[A]): Process[Task, A] =
    resource(initClient)(releaseClient)(f)

  private def initClient: Task[Client] =
    Task.delay(new Client).flatMap { client =>
      client.connect(host, port) >>
        client.login(user, password) >>
        client.enterLocalPassiveMode >>
        client.setBinaryFileType >|
        client
    }

  private def releaseClient(client: Client): Task[Unit] =
    client.quit >> client.disconnect

  import org.apache.http.client.methods.HttpPost
  import org.apache.http.impl.client.HttpClients
  import org.apache.commons.io.IOUtils
  import org.apache.http.entity.ByteArrayEntity
  import FTPWatcherMetrics._

  private val logger = LoggerFactory.getLogger(getClass)

  def whileActive[A](checkInterval: Duration)(p: Process[Task, A]): Process[Task, A] = {
    val isActive = awakeEvery(checkInterval).as(Task.delay(Config.isActive)).eval
    p.flatMap(a => sleepUntil(isActive)(emit(a)))
  }

  def uploadImage: Channel[Task, File, FailedUpload \/ FilePath] =
    Process.constant { case File(path, bytes, uploadedBy) =>
      logger.info(s"** START UPLOAD $path")
      val uri = Config.imageLoaderUri + "?uploadedBy=" + uploadedBy
      val upload = Task {
        val client = HttpClients.createDefault
        val postReq = new HttpPost(uri)
        val entity = new ByteArrayEntity(bytes)
        postReq.setEntity(entity)
        postReq.setHeader(guMediaApiHeader, mediaApiKey)
        val response = client.execute(postReq)
        val json = Json.parse(IOUtils.toByteArray(response.getEntity.getContent))
        response.close()
        client.close()
        logger.info(s"** OK UPLOADED $path")
        val imageUri = (json \ "uri").as[String]
        logger.info(s"Uploaded $path, created $imageUri")
        \/.right(path)
      }
      upload.onFinish {
        case None      => incrementUploaded(uploadedBy)
        case Some(err) => failedUploads.increment(List(causedByDimension(err)))
      }.handle {
        case NonFatal(err) => \/.left(FailedUpload(path))
      }
    }

}

case class FailedUpload(path: FilePath) extends AnyVal

case class File(path: FilePath, bytes: Array[Byte], uploadedBy: String)
