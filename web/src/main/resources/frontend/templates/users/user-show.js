var userShowTemplate = '\
  <div class="user-show--row cf"> \
    <div class="user-properties"> \
      <div class="user-properties--title">用户属性</div> \
      <br> \
      {{#properties}} \
        <dl> \
          <dt class="property-name text--light">{{propertyName}}</dt>: \
          <dd class="property-value text--light">{{propertyValue}}</dd> \
        </dl> \
        <br> \
      {{/properties}} \
    </div> \
    <div class="user-show--activity-feed"> \
      <div class="activity-feed--title">活动</div> \
      <table class="table text--light"> \
        <tbody> \
          {{#timeline}} \
          <tr> \
            <td>{{date}}</td> \
            <td>{{event_type}}</td> \
          </tr> \
          {{/timeline}} \
          {{^timeline}} \
            没有用户时间线 \
          {{/timeline}} \
        </tbody> \
      </table> \
    </div> \
  </div> \
'
