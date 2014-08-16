package wookie

import java.io.{File, FileOutputStream}
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import javafx.application.{Application, Platform}
import javafx.scene.Scene
import javafx.scene.control.{Label, ProgressBar}
import javafx.scene.layout.{Priority, VBox}
import javafx.stage.Stage

import chaschev.io.FileUtils
import chaschev.lang.LangUtils
import chaschev.util.Exceptions
import com.google.common.io.{ByteStreams, CountingOutputStream}
import org.apache.commons.io.{FilenameUtils, IOUtils}
import org.apache.commons.lang3.StringUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import org.slf4j.LoggerFactory
import wookie.view.{OkNavigationEvent, NavigationEvent, WaitArg, WookieView}

import scala.concurrent.ops._
import scala.util.Random

object DownloadFxApp3{
  final val logger = LoggerFactory.getLogger(DownloadFxApp3.getClass)



  final val downloadLatch = new CountDownLatch(1)
  private final val appStartedLatch = new CountDownLatch(1)

  final val downloadResult = new AtomicReference[DownloadResult]

  protected final val instance = new AtomicReference[DownloadFxApp3]

  @volatile
  var oracleUser: Option[String] = None

  @volatile
  var oraclePassword: Option[String] = None

  @volatile
  var version: String = "7u51"

  @volatile
  var miniMode: Boolean = false

  @volatile
  var tempDestDir: File = new File(".")

  def main(args: Array[String])
  {

    DownloadFxApp3.version = "6u45"
    DownloadFxApp3.oracleUser = Some("chaschev@gmail.com")
    DownloadFxApp3.oraclePassword = Some("Shotgun8!")

    Application.launch(classOf[DownloadFxApp3], args: _*)
  }
}

case class DownloadResult(file:Option[File], message:String, ok:Boolean){

}

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
class DownloadFxApp3 extends Application{
  def start(stage: Stage)
  {
    createScene(stage)
  }

  val progressLabel = new Label("Retrieving a link...")

  val progressBar = new ProgressBar(0)
  var browser: WookieView = null

  def createScene(stage: Stage)
  {
    try {
      browser = WookieView.newBuilder
        .useFirebug(false)
        .useJQuery(true)
        .createWebView(!DownloadFxApp3.miniMode)
        .includeJsScript(io.Source.fromInputStream(getClass.getResourceAsStream("/wookie/downloadJDK.js")).mkString)
        .build

      DownloadFxApp3.instance.set(this)
      DownloadFxApp3.appStartedLatch.countDown

      createAndShowStage(stage)

      val (latestUrl:Option[String], archiveUrl:Option[String]) = getLinksFromVersion

      whenSignonForm
      whenDownloadStarts

      // download logic
      // if there is 'the latest version page'
      if (latestUrl.isDefined) {
        tryFindVersionAtPage(browser, latestUrl.get, (found:Boolean) => {
          if(!found){
            tryArchivePage(found, archiveUrl, browser)
          }else{
            println(s"found the link at ${latestUrl.get}, download should start...")
          }
        })
      } else {
        tryArchivePage(found = false, archiveUrl, browser)
      }
    }
    catch {
      case e: Exception => e.printStackTrace
    }
  }


  def createAndShowStage(stage: Stage)
  {
    stage.setTitle("Downloading JDK " + DownloadFxApp3.version + "...")

    val vBox: VBox = new VBox()

    vBox.getChildren.addAll(progressLabel, progressBar, browser)

    //      val vBox: VBox = children.fillWidth(true).build
    val scene: Scene = new Scene(vBox)

    stage.setScene(scene)

    if (DownloadFxApp3.miniMode) {
      stage.setWidth(300)
    } else {
      stage.setWidth(1024)
      stage.setHeight(768)
    }

    stage.show()

    VBox.setVgrow(browser, Priority.ALWAYS)
  }

  private[this] def whenDownloadStarts()
  {
    browser.waitForLocation(new WaitArg()
      .timeoutNone()
      // todo: change page ready to ANY?
      .matchByPredicate((w, arg) => { w.newLoc.contains("download.oracle") && w.newLoc.contains("?")})
      .isPageReadyEvent(false)
      .whenLoaded((event) => {
      // will be here after
      // clicking accept license and link -> * not logged in * -> here -> download -> redirect to login
      // download -> fill form -> * logged in * -> here -> download
      val navEvent = event.asInstanceOf[OkNavigationEvent]

      val uri = navEvent.wookieEvent.newLoc

      spawn {
        Thread.currentThread().setName("fx-downloader")

        try {
          //there must be a shorter way to do this

          val httpClient = new DefaultHttpClient
          val httpget = new HttpGet(uri)
          val response = httpClient.execute(httpget)

          val code = response.getStatusLine.getStatusCode

          if (code != 200) {
            System.out.println(IOUtils.toString(response.getEntity.getContent))
            throw new RuntimeException("failed to download: " + uri)
          }

          val file = new File(DownloadFxApp3.tempDestDir, StringUtils.substringBefore(FilenameUtils.getName(uri), "?"))

          val httpEntity = response.getEntity
          val length = httpEntity.getContentLength

          val os = new CountingOutputStream(new FileOutputStream(file))

          println(s"Downloading $uri to $file...")

          var lastProgress = 0.0
          var isProgressRunning = true

          spawn {
            Thread.currentThread().setName("progressThread")

            while (isProgressRunning) {
              val bytesCopied = os.getCount
              val progress = bytesCopied * 100D / length

              if (progress != lastProgress) {
                val s = s"${file.getName}: ${FileUtils.humanReadableByteCount(bytesCopied, false, false)}/${FileUtils.humanReadableByteCount(length, false, true)} ${LangUtils.toConciseString(progress, 1)}%"

                setStatus(progressLabel, s)

                print("\r" + s)
              }

              lastProgress = progress
              progressBar.setProgress(bytesCopied * 1D / length)

              try {
                Thread.sleep(500)
              }
              catch {
                case e: InterruptedException => //ignore
              }
            }
          }

          ByteStreams.copy(httpEntity.getContent, os)

          isProgressRunning = false

          System.out.println("Download complete.")
          DownloadFxApp3.downloadResult.set(new DownloadResult(Some(file), "", true))
          DownloadFxApp3.downloadLatch.countDown
        }
        catch {
          case e: Exception =>
            LoggerFactory.getLogger("log").warn("", e)
            DownloadFxApp3.downloadResult.set(new DownloadResult(None, e.getMessage, false))
            throw Exceptions.runtime(e)
        }
      }
    }))
  }

  protected def whenSignonForm() =
  {
    browser.waitForLocation(new WaitArg()
      .timeoutNone()
      .matchByPredicate((w, arg) => {w.newLoc.contains("signon.jsp")})
      .whenLoaded((event) => {
//      setStatus(progressLabel, "waiting for the login form...")
//
//      Thread.sleep(1000)
//
//      browser.waitFor("$('#sso_username').length > 0", 10000)

      println(browser.$("#sso_username"))

      System.out.println("I see it all, I see it now!")

      Platform.runLater(new Runnable {
        def run()
        {
          browser.initClicksJs(Random.nextInt())
          browser.getEngine.executeScript("" +
            "alert($('#sso_username').val('" + DownloadFxApp3.oracleUser.get + "'));\n" +
            "alert($('#ssopassword').val('" + DownloadFxApp3.oraclePassword.get + "'));\n" +
            "$clickIt($('.sf-btnarea a'))"
          )
        }
      })
    }))
  }

  def getLinksFromVersion:(Option[String], Option[String]) =
  {
    val archiveLinksMap = Map(
      5 -> "http://www.oracle.com/technetwork/java/javasebusiness/downloads/java-archive-downloads-javase5-419410.html",
      6 -> "http://www.oracle.com/technetwork/java/javase/downloads/java-archive-downloads-javase6-419409.html",
      7 -> "http://www.oracle.com/technetwork/java/javase/downloads/java-archive-downloads-javase7-521261.html"
    )

    val latestLinksMap = Map(
      7 -> "http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html"
    )

    var archiveUrl: Option[String] = None
    var latestUrl: Option[String] = None

    val ch = DownloadFxApp3.version.charAt(0)

    if (List('7', '6', '5').contains(ch)) {
      latestUrl = latestLinksMap.get(ch - '0')
      archiveUrl = archiveLinksMap.get(ch - '0')
    }

    (latestUrl, archiveUrl)
  }

  def setStatus(label:Label, s: String)
  {
    Platform.runLater(new Runnable {
      def run()
      {
        label.setText(s)
      }
    })
  }

  protected def tryArchivePage(found: Boolean, archiveUrl: Option[String], browser: WookieView)
  {
    if (!found && archiveUrl.isDefined) {
      tryFindVersionAtPage(browser, archiveUrl.get, (found) => {
        if (found) {
          println("Will be redirected to login page...")
        } else {
          DownloadFxApp3.downloadResult.set(new DownloadResult(None, "didn't find a link", false))

          DownloadFxApp3.downloadLatch.countDown
        }
      })
    } else {
      System.out.println("Download started...")
    }
  }

  /**
   *
   * @param browser
   * @param archiveUrl
   * @param whenDone(found Boolean)
   */
  private[wookie] def tryFindVersionAtPage(browser: WookieView, archiveUrl: String, whenDone: (Boolean) => Unit)
  {
    browser.load(archiveUrl, (event:NavigationEvent) => {
        try {
          val aBoolean: Boolean = browser.getEngine.executeScript("downloadIfFound('" + DownloadFxApp3.version + "', true, 'linux');").asInstanceOf[Boolean]

          if (aBoolean) {
            whenDone.apply(true)
          }else {
            whenDone.apply(false)
          }
        } catch {
          case e: Exception => e.printStackTrace
        }
      })
  }
}