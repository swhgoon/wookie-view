package wookie

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ConcurrentHashMap, CountDownLatch, TimeUnit}
import javafx.application
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.layout.{Priority, VBox}
import javafx.stage.Stage

import chaschev.util.Exceptions
import org.slf4j.LoggerFactory
import wookie.view._

abstract class JQuerySupplier {
  private var event: Option[PageDoneEvent] = None

  def getEvent: PageDoneEvent = {
    if(!event.isDefined) {
      throw new IllegalStateException("event is not present")
    }

    event.get
  }

  def setEvent(event: PageDoneEvent): this.type = {
    this.event = Some(event)
    this
  }

  def apply(selector: String): JQueryWrapper
}

trait PanelSupplier {
  def apply(): WookiePanel
}



object WookieSandboxApp {
  final val logger = LoggerFactory.getLogger(WookieSandboxApp.getClass)

  private final val appStartedLatch = new CountDownLatch(1)

  protected final val instance = new AtomicReference[WookieSandboxApp]
  protected final val initialStage = new AtomicReference[Stage]

  private final val scenarios = new ConcurrentHashMap[String, WookieScenario]

  def main(args: Array[String])
  {
    Application.launch(classOf[WookieSandboxApp], args: _*)
  }
  
  def start(): WookieSandboxApp = {
    try {
      new Thread(){
        override def run(): Unit = {
          Application.launch(classOf[WookieSandboxApp], Array.empty[String]: _*)
        }
      }.start()

      appStartedLatch.await(2, TimeUnit.SECONDS)
      instance.get()
    } catch {
      case e: Exception => throw Exceptions.runtime(e)
    }
  }
  
  def setMainScenario(ws: WookieScenario) = {
    scenarios.put(ws.title, ws)
  }
}

class WookieStage(stage: Stage, wookiePanel: WookiePanel) {
  {
//    val scene = new Scene(new VBox(new Text("keke")))
    val scene = new Scene(wookiePanel)

    stage.setScene(scene)
    stage.setWidth(1024)
    stage.setHeight(768)

    VBox.setVgrow(wookiePanel, Priority.ALWAYS)
  }

  def show() = stage.show()
}

class WookieSandboxApp extends Application {
  def start(stage: Stage)
  {
    WookieSandboxApp.instance.set(this)
    WookieSandboxApp.initialStage.set(stage)

//    new WookiePanel(stage, )
  }

  def runOnStage(ws: WookieScenario) = {
    application.Platform.runLater(new Runnable {
      override def run(): Unit = {
        val panel = ws.newPanel()

        val stage = new Stage()

        stage.setTitle(ws.title)

        new WookieStage(stage, panel).show()

//        if(ws.init.isDefined) ws.init.get.apply()

        val wookie = panel.wookie

        wookie.currentScenario = ws

        new Thread(new Runnable {
          override def run(): Unit = {
            if(ws.url.isDefined) {
              wookie.load(ws.url.get, new WhenPageLoaded {
                override def apply()(implicit e: PageDoneEvent): Unit = {
                  ws.procedure(panel, wookie, wookie.createJSupplier(Some(ws.url.get), Some(e)))
                }
              })
            } else {
              ws.procedure(panel, wookie, wookie.createJSupplier(None, None))
            }
          }
        }, "wookie scenario thread").start()
      }
    })
  }
}
