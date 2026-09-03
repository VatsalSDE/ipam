var topManager =
{
    menu :
    {
        IpRequests:"ipRequests",RogueDetection: "rogueDetection", Reports : 'reports', EventLog : 'eventLog', Alert : 'alerts', Settings : 'settings', Bell : 'bell', User : 'user', Home : 'home'
    },
    globalSearch:undefined,

    // -------------------------------------------------------------------------------Top panel init-------------------------------------------------------------------------------------------//

    init : function ()
    {
        flux.bindKendoButtonClickEvent({element: 'leftHeader a'}, topManager.loadHomeScreen);

        flux.bindKendoButtonClickEvent({element: 'header li a'}, topManager.onTopButtonClick);

        topManager.applyRoleBasedVisibility();

        topManager.startAlertStream();

        var globalSearch = $("#globalSearch");

        globalSearch.empty();

        globalSearch.on('keypress',function (event)
        {
            if(event.keyCode == 13)
            {
                topManager.onGlobalSearchClick(event);
            }
        });
    },

    updateAlertBadge : function (newTotal)
    {
        var token = appManager.getCookie("token");
        if (!token) return;

        if (newTotal !== undefined) {
            var badge = $("#alertNotificationBadge");
            if (badge.length === 0) {
                $('#alertsMenu a').css('position', 'relative').append('<span id="alertNotificationBadge" class="badge badge-danger" style="position: absolute; top: -5px; right: -5px; font-size: 10px; background: #FF4D4D; color: white; border-radius: 10px; padding: 2px 6px; display: none;"></span>');
                badge = $("#alertNotificationBadge");
            }
            if (newTotal > 0) {
                badge.text(newTotal > 99 ? '99+' : newTotal).show();
            } else {
                badge.hide();
            }
            return;
        }

        $.ajax({
            url: '/api/alerts?activeOnly=true&limit=1',
            type: 'GET',
            headers: { 'Authorization': 'Bearer ' + token },
            success: function(res) {
                var total = (res && res.data && res.data.total !== undefined) ? res.data.total : (res && res.total !== undefined ? res.total : 0);
                var badge = $("#alertNotificationBadge");
                if (badge.length === 0) {
                    $('#alertsMenu a').css('position', 'relative').append('<span id="alertNotificationBadge" class="badge badge-danger" style="position: absolute; top: -5px; right: -5px; font-size: 10px; background: #FF4D4D; color: white; border-radius: 10px; padding: 2px 6px; display: none;"></span>');
                    badge = $("#alertNotificationBadge");
                }
                if (total > 0) {
                    badge.text(total > 99 ? '99+' : total).show();
                } else {
                    badge.hide();
                }
            }
        });
    },

    startAlertStream : function ()
    {
        // Initial badge count
        topManager.updateAlertBadge();

        // Connect Server-Sent Events stream for instant real-time push
        try {
            if (window.EventSource) {
                var token = appManager.getCookie("token");
                var streamUrl = '/api/alerts/stream' + (token ? ('?token=' + encodeURIComponent(token)) : '');
                var source = new EventSource(streamUrl);

                source.addEventListener('alert', function(e) {
                    try {
                        var alert = JSON.parse(e.data);
                        // 1. Show notification toast popup on ANY page
                        if (typeof notification !== 'undefined' && notification.showNotification) {
                            notification.showNotification({
                                notificationTitle: "⚠️ Alert: " + (alert.message || alert.alertType || "Network Alert"),
                                notificationType: "warning"
                            });
                        }
                        // 2. Increment Bell Counter Badge
                        topManager.updateAlertBadge();

                        // 3. If user is currently looking at the Alerts page, auto-reload table
                        if (window.alerts && $("#alertsTable").length && $("#alertsTable").is(":visible")) {
                            var filter = $("#alertFilter").data("kendoDropDownList") ? $("#alertFilter").data("kendoDropDownList").value() : "live";
                            alerts.renderAlertsGrid({ alertFilter: filter });
                        }
                    } catch (err) {
                        console.error("Error processing alert stream event:", err);
                    }
                });

                source.addEventListener('alert_cleared', function() {
                    topManager.updateAlertBadge();
                    if (window.alerts && $("#alertsTable").length && $("#alertsTable").is(":visible")) {
                        var filter = $("#alertFilter").data("kendoDropDownList") ? $("#alertFilter").data("kendoDropDownList").value() : "live";
                        alerts.renderAlertsGrid({ alertFilter: filter });
                    }
                });

                source.onerror = function() {
                    // Browser automatically reconnects SSE streams
                };
            }
        } catch (e) {
            console.warn("SSE not available, using periodic poller fallback:", e);
        }

        // Lightweight background fallback poller every 30 seconds
        if (!topManager._alertPollerInterval) {
            topManager._alertPollerInterval = setInterval(function() {
                topManager.updateAlertBadge();
            }, 30000);
        }
    },

    applyRoleBasedVisibility : function ()
    {
        var userRole = appManager.getCookie("userRole") || "ROLE_ADMIN";
        var isAdmin = (userRole === "ROLE_ADMIN" || userRole === "ADMIN");
        if (!isAdmin) {
            $('#settingsMenu').hide();
            $('#rogueDetectionMenu').hide();
            $('#alertsMenu').hide();
            $('#eventLogMenu').hide();
        } else {
            $('#settingsMenu, #rogueDetectionMenu, #alertsMenu, #eventLogMenu').show();
        }
    },

    // --------------------------------------------------------------------------------Search value entered and load global search grid------------------------------------------------------------------------------------------//

    onGlobalSearchClick : function (event)
    {
        if(event )
        {
            var searchValue = $("#globalSearch").val();

            if(searchValue)
            {
                if(searchValue.length>20)
                {
                    notification.showNotification({notificationTitle:"Maximum 20 characters are allowed.",notificationType:"error"});
                }
                else
                {
                    var header = $("#header");

                    header.find('li.active').removeClass("active");

                    var breadCrumbMenu = navigationManager.getUrlParameter("navigation");

                    globalSearch.renderGlobalSearch(searchValue,breadCrumbMenu);
                }
            }
        }
    },

    // -------------------------------------------------------------------------------Init home page on IP Address Manager btn click-------------------------------------------------------------------------------------------//

    loadHomeScreen : function (event)
    {
        if(event)
        {
            event.event.preventDefault();

            navigationManager.addHistory("navigation=home");

            homeManager.init();
        }
    },

    // --------------------------------------------------------------------------------Bind top btn event------------------------------------------------------------------------------------------//

    onTopButtonClick : function (event)
    {
        if(event)
        {
            event.event.preventDefault();

            var menuId = $(event.event.currentTarget).data('value');

            topManager.setActiveMenu(menuId);

            switch (menuId)
            {
                case topManager.menu.Home :

                    homeManager.init();

                    navigationManager.addHistory("navigation=home");

                    break;

                case topManager.menu.Settings :

                    navigationManager.addHistory("navigation=settings~~settingsTab=dhcpManagement");

                    admin.init();

                    break;

                case topManager.menu.Reports:

                    navigationManager.addHistory("navigation=reports");

                    report.init();

                    break;

                case topManager.menu.EventLog :

                    navigationManager.addHistory("navigation=eventLog");

                    eventLog.init();

                    break;

                case topManager.menu.Alert :

                    navigationManager.addHistory("navigation=alerts");

                    alerts.init();

                    break;

                case topManager.menu.RogueDetection :

                    navigationManager.addHistory("navigation=rogueDetection");

                    rogueDetection.init();

                    break;

                case topManager.menu.IpRequests :

                    navigationManager.addHistory("navigation=ipRequests");

                    ipRequests.init();

                    break;
            }
        }
    },

    // -----------------------------------------------------------------------------Add top panel active class---------------------------------------------------------------------------------------------//

    setActiveMenu : function (element)
    {
        var header = $("#header");

        header.find('li.active').removeClass("active");

        header.find('li:has(a[data-value=' + element + '])').addClass("active");
    }
};