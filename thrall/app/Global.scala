
import play.api.{Application, GlobalSettings}
import lib.{ElasticSearch, ThrallMessageConsumer}


object Global extends GlobalSettings {

  override def beforeStart(app: Application) {
    ElasticSearch.ensureAliasAssigned()
    ThrallMessageConsumer.startSchedule()
  }

  override def onStop(app: Application) {
    ThrallMessageConsumer.actorSystem.shutdown()
  }

}
