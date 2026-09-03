var alerts = {

    AlertsPage: 'alertsPage', AlertsTable : 'alertsTable',

    init : function ()
    {
        loaderUtil.showModalLoader();

        loaderUtil.showCentralModalLoader(appConstant.LoadingMessage);

        navigationManager.addHistory("navigation=alerts");

        topManager.setActiveMenu('alerts');

        var root = $("#header_panel");

        root.empty();

        root.html('<div class="title-inner-box"> Alerts </div>');

        $("#container-panel").html('<div id="leftPanel" class="left-panel alertsPage"></div><div id="alertsPage" class="content-panel"></div><div id="right-panel" class="right-panel stickyScrollRight"></div>');

        appManager.togglePanel();

        appManager.renderHTML(alerts.AlertsPage, $("#alertsPage"),undefined);

        var reportDropDown = $("#alertFilter");

        var data = [
            { text: "Live", value: "live" },
            { text: "Clear", value: "clear" }
        ];

        reportDropDown.kendoDropDownList({
            dataTextField: "text",
            dataValueField: "value",
            dataSource: data,
            value: "live",
            change: function (e) {
                if (e) {
                    e.preventDefault();
                }
                var val = this.value();
                alerts.renderAlertsGrid({ alertFilter: val });
            }
        });

        alerts.renderAlertsGrid({ alertFilter: "live" });
    },

    // ----------------------------------------------------------------------Load Alerts grid----------------------------------------------------------------------------------------------------//

    renderAlertsGrid : function (param)
    {
        var gridId = $('#'+alerts.AlertsTable);

        var callbackContexts = {

            Read: function (options)
            {
                param.page = options.data.page;
                param.pageSize = options.data.pageSize;
                appManager.executeGETRequest({url: '/alerts/',container:options,callback:alerts.renderAlertsGridData,params:param});
            },
            EventId: alerts.AlertsTable,
            PageSize: 20,
            pageable: {
                refresh: true,
                pageSizes: [10,20,50,100],
                buttonCount: 10
            },
            DataType: 'json',
            groupable: true,
            schema: {
                model: {
                    id: "id",
                    fields: {
                        id: { type: "number" },
                        alertType: { type: "string" },
                        message: { type: "string" },
                        subnet: { type: "string" },
                        timestamp: { type: "string" },
                        status: { type: "boolean" }
                    }
                }
            },
            Fields: [
                {
                    field: "alertType",
                    title: "Alert Type",
                    width: "15%",
                    template: function(d) {
                        var v = d.alertType || "";
                        return '<span title="' + v + '">' + v + '</span>';
                    }
                },
                {
                    field: "message",
                    title: "Message",
                    width: "45%",
                    template: function(d) {
                        var v = d.message || "";
                        return '<span title="' + v + '">' + v + '</span>';
                    }
                },
                {
                    field: "subnet",
                    title: "Subnet",
                    width: "15%",
                    template: function(d) {
                        var v = d.subnet || "";
                        return '<span title="' + v + '">' + v + '</span>';
                    }
                },
                {
                    field: "timestamp",
                    title: "Times",
                    width: "13%",
                    template: function(d) {
                        var v = d.timestamp || "";
                        return '<span title="' + v + '">' + v + '</span>';
                    }
                },
                {
                    field: "actions",
                    title: "Actions",
                    width: "12%",
                    template: function(d) {
                        if (d.status === true || d.status === 1 || d.status === "1" || d.status === "true") {
                            return '<button class="btn btn-xs btn-success" onclick="alerts.clearAlert(' + d.id + ')" title="Clear Alert" style="padding: 2px 8px; font-size: 11px; cursor: pointer;">Clear</button>';
                        } else {
                            return '<button class="btn btn-xs btn-danger" onclick="alerts.deleteAlert(' + d.id + ')" title="Delete Alert" style="padding: 2px 8px; font-size: 11px; cursor: pointer;">Delete</button>';
                        }
                    }
                }
            ],
            sortable: true,
            resizable: true
        };

        // Destroy old grid context
        try {
            gridId.data().kendoGrid.destroy();
            gridId.empty();
        }
        catch(err)
        {
        }

        widgetRenderManager.renderGridDataWithPaging(callbackContexts);

        formManager.searchFilter(gridId);
    },

    // ----------------------------------------------------------------------Render Alert grid----------------------------------------------------------------------------------------------------//

    renderAlertsGridData : function (context)
    {
        if (context && context.json && context.json.data != null && context.json.success == true)
        {
            var raw = context.json.data;
            var list = [];
            var totalCount = 0;

            if (Array.isArray(raw)) {
                list = raw;
                totalCount = raw.length;
            } else if (raw && Array.isArray(raw.alerts)) {
                list = raw.alerts;
                totalCount = raw.total !== undefined ? raw.total : raw.alerts.length;
            } else if (raw && Array.isArray(raw.data)) {
                list = raw.data;
                totalCount = raw.total !== undefined ? raw.total : raw.data.length;
            }

            if (context.container && context.container.success) {
                context.container.success({ data: list, total: totalCount });
            }

            if (list.length === 0) {
                $(".k-grid-content").html(appConstant.NoDataSpan);
            }
        }
        else
        {
            if (context && context.container && context.container.success) {
                context.container.success({ data: [], total: 0 });
            }

            $(".k-grid-content").html(appConstant.NoDataSpan);
        }

        try {
            loaderUtil.hideModalLoader();
            loaderUtil.hideCentralModalLoader();
        } catch (e) {}
    },

    clearAlert: function (id) {
        if (!id) return;
        $.ajax({
            url: '/api/alerts/' + id + '/clear',
            type: 'PUT',
            headers: { 'Authorization': 'Bearer ' + appManager.getCookie("token") },
            success: function (res) {
                if (window.notification && notification.showNotification) {
                    notification.showNotification('success', 'Alert cleared successfully');
                }
                var currentFilter = $("#alertFilter").data("kendoDropDownList") ? $("#alertFilter").data("kendoDropDownList").value() : "live";
                alerts.renderAlertsGrid({ alertFilter: currentFilter });
                if (window.topManager && topManager.updateAlertBadge) {
                    topManager.updateAlertBadge();
                }
            },
            error: function () {
                if (window.notification && notification.showNotification) {
                    notification.showNotification('error', 'Failed to clear alert');
                }
            }
        });
    },

    deleteAlert: function (id) {
        if (!id) return;
        $.ajax({
            url: '/api/alerts/' + id,
            type: 'DELETE',
            headers: { 'Authorization': 'Bearer ' + appManager.getCookie("token") },
            success: function (res) {
                if (window.notification && notification.showNotification) {
                    notification.showNotification('success', 'Alert permanently deleted');
                }
                var currentFilter = $("#alertFilter").data("kendoDropDownList") ? $("#alertFilter").data("kendoDropDownList").value() : "clear";
                alerts.renderAlertsGrid({ alertFilter: currentFilter });
                if (window.topManager && topManager.updateAlertBadge) {
                    topManager.updateAlertBadge();
                }
            },
            error: function () {
                if (window.notification && notification.showNotification) {
                    notification.showNotification('error', 'Failed to delete alert');
                }
            }
        });
    },

    // ---------------------------------------------------------------------------Navigation-----------------------------------------------------------------------------------------------//

    renderAlertsFromURL : function ()
    {
        alerts.init();
    }
};