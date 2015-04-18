package lib

import scalaz.stream._
import scalaz.concurrent.Task

// Borrowed from http://eugenezhulenev.com/blog/2014/04/01/scalaz-stream-concurrent-process/
trait Concurrency {
  implicit class ConcurrentProcess[O](val process: Process[Task, O]) {
    /**
     * Run process through channel with given level of concurrency
     */
    def concurrently[O2](concurrencyLevel: Int)
                        (f: Channel[Task, O, O2]): Process[Task, O2] = {
      val actions =
        process.
          zipWith(f)((data, f) => f(data))

      val nestedActions =
        actions.map(Process.eval)

      merge.mergeN(concurrencyLevel)(nestedActions)
    }
  }

}
