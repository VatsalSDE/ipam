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