var $clickIt = function(sel){
    clickIt(jQuery(sel))
}

var clickIt = function($el){
  var el = $el[0];
  var etype = 'click';

  clickDom(el, etype);
}

var submitEnclosingForm = function(sel){
    jQuery(sel).closest('form').submit();
}

var pressKey = function(sel, code){
    var p = {which: code, keyCode: code};

    jQuery(sel).trigger(jQuery.Event("keydown", p));
    jQuery(sel).trigger(jQuery.Event("keyup", p));
    jQuery(sel).trigger(jQuery.Event("keypress", p));
//    alert("triggered " + JSON.stringify(p));
}

var clickDom = function(el, etype){
  if (el.fireEvent) {
    el.fireEvent('on' + etype);
  } else {

    var evObj = document.createEvent('Events');

    evObj.initEvent(etype, true, false);

    el.dispatchEvent(evObj);
  }
}

var printJQuery = function($sel){
    var r = $($sel);
    alert("found " + r.length + " results for " + $sel + ": " + r.html());

    r.each(function(index, el){
        alert(el.outerHTML);
    });
}

var newArrayFn = function(i){
    return function($sel){
        return $($($sel)[i]);
    };
}

var arrayFn = function($sel){
    return $($(sel)[i]);
}

var jQueryAggregate = function(operationFn, $sel, initialValue, aggregator){
    var r = operationFn($sel);

    var result = initialValue

    r.each(function(index, el){
        result = aggregator(result, index, el);
    });

    return result;
}

var jQuery_asResultArray = function(operationFn,$sel){
    var res = [];

    jQueryAggregate(operationFn, $sel, res, function(r, i, el){
        r.push(jQuery(el));
        return r;
    });

    return res;
};

var jQueryAttrs = function(operationFn, $sel){
    var r = operationFn($sel);

    if(r.length == 0) return [];

    var nodes=[], values=[];
    var el =  r[0];

    for (var attr, i=0, attrs=el.attributes, l=attrs.length; i<l; i++){
        attr = attrs.item(i)
        nodes.push(attr.nodeName);
        values.push(attr.nodeValue);
    }

    return nodes;
};

//var jQuery_asResultArray = function($sel){
//
//};

var jQuery_text = function(operationFn, $sel, isHtml){
    return jQueryAggregate(operationFn, $sel, '', function(r, i, el){
        if(isHtml) {
            return r + el.outerHTML + "\n";
        } else {
            return r + el.innerText + "\n";
        }
    });
};