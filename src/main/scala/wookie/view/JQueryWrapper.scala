package wookie.view

import java.util.concurrent.{TimeoutException, TimeUnit, CountDownLatch}

import netscape.javascript.JSObject
import org.apache.commons.lang3.StringEscapeUtils

import scala.collection.mutable
import scala.util.Random

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
/**
 * An abstract wrapper for i.e. find in "$(sel).find()" or array items: $($(sel)[3])
 */
abstract class CompositionJQueryWrapper(selector:String, wookie:WookieView) extends JQueryWrapper(selector, wookie){

}

class ArrayItemJQueryWrapper(selector:String, index:Int, wookie:WookieView) extends CompositionJQueryWrapper(selector, wookie){
  val function = s"newArrayFn($index)"
}

class FindJQueryWrapper(selector:String, findSelector:String,  wookie:WookieView) extends CompositionJQueryWrapper(selector, wookie){
  //  private final val escapedFindSelector = StringEscapeUtils.escapeEcmaScript(findSelector)
  private final val escapedFindSelector = StringEscapeUtils.escapeEcmaScript(findSelector)
  val function = s"newFindFn('$escapedSelector', '$escapedFindSelector')"
}

class DirectWrapper(isDom:Boolean = false, jsObject:JSObject,  wookie:WookieView) extends CompositionJQueryWrapper("", wookie){
  val function = "directFn"

  private def assign() = {
    val engine = wookie.getEngine
    val window = engine.executeScript("window").asInstanceOf[JSObject]

    if(!isDom){
      window.setMember("__javaToJS", jsObject)
    }else{
      window.setMember("__javaToJS", jsObject)

      engine.executeScript("window.__javaToJS = jQuery(window.__javaToJS); window.__javaToJS")
    }
  }

  override def interact(script: String, timeoutMs: Long): AnyRef = {
    assign()
    super.interact(script, timeoutMs)
  }
}

class SelectorJQueryWrapper(selector:String, wookie:WookieView) extends JQueryWrapper(selector, wookie){
  val function = "jQuery"
}

abstract class JQueryWrapper(selector:String, wookie:WookieView){
  val function:String

  val escapedSelector = StringEscapeUtils.escapeEcmaScript(selector)

  def attr(name: String): String = {
    interact(s"jQueryGetAttr($function, '$escapedSelector', '$name')").asInstanceOf[String]
  }

  def attrs(): List[String] = {
    interact(s"jQueryAttrs($function, '$escapedSelector')").asInstanceOf[List[String]]
  }

  def text(): String = {
    interact(s"jQuery_text($function, '$escapedSelector', false)".toString).asInstanceOf[String]
  }

  def html(): String = {
    interact(s"jQuery_text($function, '$escapedSelector', true)".toString).asInstanceOf[String]
  }

  def clickLink(): JQueryWrapper = {
    interact(s"clickItem($function, '$escapedSelector')".toString)
    this
  }

  def mouseClick(): JQueryWrapper = {
    triggerEvent("click")
  }


  def triggerEvent(event: String): JQueryWrapper ={
    interact(s"$function('$escapedSelector').trigger('$event')".toString)
    this
  }

  def find(findSelector: String): List[JQueryWrapper] = {
    val escapedFindSelector = StringEscapeUtils.escapeEcmaScript(findSelector)

    _jsJQueryToResultList(interact(s"jQueryFind($function, '$escapedSelector', '$escapedFindSelector')").asInstanceOf[JSObject])
  }

  def parent(): List[JQueryWrapper] = {
    _jsJQueryToResultList(interact(s"$function('$escapedSelector').parent()").asInstanceOf[JSObject])
  }


  def attr(name:String, value:String):JQueryWrapper = {
    interact(s"jQuerySetAttr($function, '$escapedSelector', '$name', '$value')")
    this
  }

  def value(value:String):JQueryWrapper = {
    interact(s"jQuerySetValue($function, '$escapedSelector').val('$value')")
    this
  }

  def submit():JQueryWrapper = {
    interact(s"submitEnclosingForm($function, '$escapedSelector')")
    this
  }

  protected def _jsJQueryToResultList(r: JSObject): List[JQueryWrapper] =
  {
    var l = r.getMember("length").asInstanceOf[Int]

    val list = new mutable.MutableList[JQueryWrapper]

    println(s"jQuery object, length=$l")

    for (i <- 0 until l) {
      //      list += new ArrayItemJQueryWrapper(selector, i, wookie)
      val slot = r.getSlot(i)

      println(slot, i)

      val sObject = slot.asInstanceOf[JSObject]

      list += new DirectWrapper(true, sObject, wookie)
    }

    list.toList
  }

  protected def _jsJQueryToDirectResultList(r: JSObject): List[JQueryWrapper] =
  {
    var l = r.getMember("length").asInstanceOf[Int]

    val list = new mutable.MutableList[JQueryWrapper]

    println(s"jQuery object, length=$l")

    for (i <- 0 until l) {
      list += new DirectWrapper(true, r.getSlot(l).asInstanceOf[JSObject], wookie)
    }

    list.toList
  }

  def pressKey(code:Int):JQueryWrapper = {
    interact(s"pressKey('$escapedSelector', $code)")
    this
  }

  def pressEnter():JQueryWrapper = {
    pressKey(13)
    this
  }

  def interact(script: String, timeoutMs: Long = 5000): AnyRef = {
    WookieView.logger.debug(s"interact: $script")

    val url = wookie.getEngine.getDocument.getDocumentURI
    val eventId = Random.nextInt()

    val latch = new CountDownLatch(1)

    var r:AnyRef = null

    wookie.includeStuffOnPage(eventId, url, Some(() => {
      WookieView.logger.debug(s"executing $script")
      r = wookie.getEngine.executeScript(script)
      latch.countDown()
    }))

    r = wookie.getEngine.executeScript(script)
    latch.countDown()

    val started = System.currentTimeMillis()
    var expired = false

    while(!expired && !latch.await(1000, TimeUnit.MILLISECONDS) ){
      if(System.currentTimeMillis() - started > timeoutMs) expired = true
    }

    if(expired){
      throw new TimeoutException(s"JS was not executed in ${timeoutMs}ms")
    }

    WookieView.logger.trace(s"left interact: $script")

    r
  }

  def html(s: String):JQueryWrapper = {
    this
  }

  def append(s:String):JQueryWrapper = {
    this
  }

  def asResultList():List[JQueryWrapper] = {
    val r = interact(s"$function('$escapedSelector')").asInstanceOf[JSObject]

    _jsJQueryToResultList(r)
  }

  override def toString: String = html()
}