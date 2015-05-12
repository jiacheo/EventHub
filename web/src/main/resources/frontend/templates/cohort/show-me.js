var showMeTemplate = ' \
  <div class="show-me text--light"> \
    <p>从节点</p> \
    &nbsp {{> event}} &nbsp \
    <p>到节点</p> \
    &nbsp {{> event}} &nbsp \
    <p>隔了</p> \
    &nbsp \
    <span> \
      <input class="two-digits" id="daysLater" type="text" name="daysLater" value="{{daysLater}}"> \
    </span> \
    &nbsp <p>天的留存率</p>\
  </div> \
';
