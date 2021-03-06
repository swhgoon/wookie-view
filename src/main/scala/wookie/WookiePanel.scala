package wookie

import java.lang
import java.text.SimpleDateFormat
import java.util.Date
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.concurrent.Worker
import javafx.event.{ActionEvent, EventHandler}
import javafx.scene.control.{Button, TextArea, TextField}
import javafx.scene.input.{KeyCode, KeyEvent}
import javafx.scene.layout.{HBox, Priority, VBox}
import javafx.scene.web.WebErrorEvent

import wookie.WookiePanel.JS_INVITATION
import wookie.view.WookieView

object WookiePanel{
  final val JS_INVITATION = "Enter JavaScript here, i.e.: alert( $('div:last').html() )"

  def newBuilder(wookie: WookieView): WookiePanelBuilder = {
    new WookiePanelBuilder(wookie)
  }
}

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
class WookiePanel(builder: WookiePanelBuilder) extends VBox with WookiePanelFields[WookiePanel]{
  val self = this

  val location = new TextField(builder.startAtPage.getOrElse(""))
  val jsArea = new TextArea()
  val logArea = new TextArea()

  val prevButton = new Button("<")
  val nextButton = new Button(">")

  val jsButton = new Button("Run JS")
  val go = new Button("Go")

  val wookie = builder.wookie
  val engine = wookie.getEngine

  val goAction = new EventHandler[ActionEvent] {
    def handle(arg0: ActionEvent)
    {
      builder.wookie.load(location.getText)
    }
  }

  {
    jsArea.setPrefRowCount(2)
    jsArea.appendText(JS_INVITATION)

    jsArea.focusedProperty().addListener(new ChangeListener[lang.Boolean] {
      def changed(observableValue: ObservableValue[_ <: lang.Boolean], s: lang.Boolean, newValue: lang.Boolean)
      {
        if(newValue){
          if(jsArea.getText.equals(JS_INVITATION)) jsArea.setText("")
        }else{
          if(jsArea.getText.trim.equals("")) jsArea.setText(JS_INVITATION)
        }
      }
    })

    def runJS()  {
      val r = engine.executeScript(jsArea.getText)

      if(r != "undefined") log(s"exec result: $r")
    }

    jsButton.setOnAction(new EventHandler[ActionEvent] {
      def handle(e:ActionEvent){
        runJS()
      }
    })

    prevButton.setOnAction(new EventHandler[ActionEvent] {
      def handle(e:ActionEvent){
        engine.getHistory.go(-1)
      }
    })

    nextButton.setOnAction(new EventHandler[ActionEvent] {
      def handle(e:ActionEvent){
        engine.getHistory.go(+1)
      }
    })

    jsArea.addEventHandler(KeyEvent.KEY_PRESSED, new EventHandler[KeyEvent] {
      def handle(e:KeyEvent){
        if (e.getCode.equals(KeyCode.ENTER) && e.isControlDown) { // CTRL + ENTER
          runJS()
        }
      }
    })

    HBox.setHgrow(location, Priority.ALWAYS)

    go.setOnAction(goAction)


    if (builder.showDebugPanes) {
      engine.locationProperty.addListener(new ChangeListener[String] {
        def changed(observableValue: ObservableValue[_ <: String], s: String, newLoc: String)
        {
          log(s"location changed to: $newLoc, from $s")

          location.setText(newLoc)
        }
      })

      engine.getLoadWorker.stateProperty.addListener(new ChangeListener[Worker.State] {
        def changed(ov: ObservableValue[_ <: Worker.State], t: Worker.State, newState: Worker.State) {
          if (newState eq Worker.State.SUCCEEDED) {
            log(s"worker state -> SUCCEEDED, page ready: ${engine.getDocument.getDocumentURI}")
          }
        }
      })


            // setOnAlert, maybe??
      // see also setOnAlert in WookieView
      engine.setOnError(new EventHandler[WebErrorEvent] {
        def handle(webEvent: WebErrorEvent)
        {
          WookieView.logOnAlert(webEvent.getMessage)
        }
      })
    }

    val toolbar = new HBox
    toolbar.getChildren.addAll(prevButton, nextButton, jsButton, location, go)
    toolbar.setFillHeight(true)

    val vBox = this

    vBox.getChildren.add(toolbar)

    if(builder.showDebugPanes) vBox.getChildren.add(jsArea)

    if(builder.userPanel.isDefined) {
      vBox.getChildren.add(builder.userPanel.get)
    }

    vBox.getChildren.add(builder.wookie)

    if(builder.showNavigation) vBox.getChildren.add(logArea)

    vBox.setFillWidth(true)

    VBox.setVgrow(builder.wookie, Priority.ALWAYS)
  }

  def log(s:String) = {
    val sWithEOL = if(s.endsWith("\n")) s else s + "\n"

    logArea.appendText(s"$nowForLog $sWithEOL")

    WookieView.logger.info(s)
  }

  def nowForLog: String =
  {
    new SimpleDateFormat("hh:mm:ss.SSS").format(new Date())
  }
}

trait WookiePanelFields[SELF]{
  var startAtPage: Option[String] = None
  var showNavigation: Boolean = true
  var showDebugPanes: Boolean = true
  var userPanel: Option[VBox] = None

  //todo change SELF to this.type
  def showNavigation(b: Boolean): SELF = { showNavigation = b; self()}
  def showDebugPanes(b: Boolean): SELF = { showDebugPanes = b; self()}
  def startAtPage(url:String): SELF = { startAtPage = Some(url); self()}
  def userPanel(vbox: VBox): SELF = { userPanel = Some(vbox); self()}

  def self(): SELF
}

class WookiePanelBuilder(val wookie: WookieView) extends WookiePanelFields[WookiePanelBuilder]{
  val self = this

  def build: WookiePanel = new WookiePanel(this)
}
