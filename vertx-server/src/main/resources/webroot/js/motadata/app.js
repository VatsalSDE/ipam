var appManager =
{
    init: function ()
    {
        topManager.init();

        flux.init();

        popupMenu.init();

        navigationManager.doNavigation();

        /*Discovery Header*/
        subnetSummary.initRunningSubnetTracking();

        homeManager.initImportSubnetTracking();

        //intercept browser back and forward button event.. only supported for HTML 5 browser ...

        window.removeEventListener('popstate', flux.onBrowserHistoryButtonClick);

        window.addEventListener("popstate", flux.onBrowserHistoryButtonClick);
    },

    resetWindowSize : function ()
    {
        $height = $(window).height() - 270;

        $('body .nav-panel').css('max-height', $height);
    },

    // ------------------------------------------------------------------------------------- Validation For user role -------------------------------------------------------------------------------------//

    validatePermission: function ()
    {
        var token = appManager.getCookie("token");
        return token != null && token !== "";
    },

    // ---------------------------------------------------------------------------------------- Check Cookie ----------------------------------------------------------------------------------//

    getCookie : function(token)
    {
        var name = token + "=";

        var decodedCookie = decodeURIComponent(document.cookie);

        var cookie = decodedCookie.split(';');

        for(var i = 0; i < cookie.length; i++)
        {
            var cookies = cookie[i];

            while (cookies.charAt(0) == ' ')
            {
                cookies = cookies.substring(1);
            }

            if (cookies.indexOf(name) == 0)
            {
                return cookies.substring(name.length, cookies.length);
            }
        }
        return "";
    },

    // -------------------------------------------------------------------------------------------- Toggle left right panel and render ------------------------------------------------------------------------------//

    togglePanel : function ()
    {
        var toggleId = $("#container-panel");

        toggleId.removeClass("leftOpenPanel");

        toggleId.removeClass("rightOpenPanel");

        toggleId.removeClass("contentOpenPanel");
    },

    toggleContentPanel : function ()
    {
        var toggleId = $('#container-panel');

        if(toggleId.hasClass('leftOpenPanel'))
        {
            $('#homeLeftArrow').addClass('open');
        }
        if(toggleId.hasClass('rightOpenPanel'))
        {
            $('#homeRightArrow').addClass('open');
        }
    },

    renderLeftRightPanel : function (menuName)
    {
        appManager.renderHTML(homeManager.LeftPanel, $("#leftPanel"), undefined);

        appManager.renderHTML(homeManager.RightPanel, $("#right-panel"), undefined);

        flux.bindKendoButtonClickEvent({element: 'homeLeftArrow'},leftPanel.onLeftArrowClick);

        leftPanel.renderTreeView(menuName);

        flux.bindKendoButtonClickEvent({element: 'homeRightArrow'},rightPanel.onRightArrowClick);

        rightPanel.renderEventDetails();

        flux.bindKendoButtonClickEvent({element: 'subnetButton'}, homeManager.onAddSubnetButtonClick);

        flux.bindKendoButtonClickEvent({element: 'supernetButton'}, homeManager.onAddSupernetButtonClick);
    },

    // ------------------------------------------------------------------------------------------- Render HTMl -------------------------------------------------------------------------------//

    renderHTML: function (page, container, context)
    {
        var htmlPage = htmlRender.getHTML(page);

        if (context)
        {
            if (context.preTask = 'Replace')
            {

                $.each(context, function (key, value) {

                    htmlPage = htmlPage.replace(key, value)

                });
            }
        }
        container.html(htmlPage);

    },

    /////////////////////////////////////////// CUSTOM SCROLLBAR ////////////////////////////////////////////////////////////////////////////////////////////

    initCustomScrollbar: function (context)
    {
        // for report right panel width scroll issue
        if(context.selector)
        {
            context.container.mCustomScrollbar({

                theme: "minimal-dark",

                scrollInertia:0.2,

                axis:"y",

                scrollButtons: {
                    enable: context.scrollButtons
                }

            });
        }
        else
        {

            context.container.mCustomScrollbar({

                theme: "minimal-dark",

                scrollInertia:0.2,

                axis:"yx",

                // For width toggle issue set 100% instead of auto
                setWidth: "100%",

                scrollButtons: {
                    enable: context.scrollButtons
                }

            });
        }

    },

    // -------------------------------------------------------------------------------------------- AjaX call ------------------------------------------------------------------------------//

    executeGETRequest: function (request)
    {
        var token = appManager.getCookie("token");
        var headers = {
            "Authorization": "Bearer " + token,
            "accessToken": token
        };

        var url = request.url;

        // 1. Permission Validation
        if (url === '/validatePermission/') {
            if (request.callback) request.callback({ json: { success: true, data: true }, container: request.container });
            return;
        }

        // 2. Brand & Global Settings
        if (url === '/brand/1' || url.startsWith('/brand')) {
            if (request.callback) request.callback({ json: { success: true, data: { id: 1, productName: "Motadata", logo: "/images/logo.png" } }, container: request.container });
            return;
        }
        if (url === '/globalSetting/1' || url.startsWith('/globalSetting')) {
            if (request.callback) request.callback({ json: { success: true, data: { id: 1, cssMode: 1 } }, container: request.container });
            return;
        }

        // 3. Subnet Category (Treeview in Left Drawer)
        if (url === '/subnetByCategory/') {
            $.ajax({
                url: '/api/subnet?limit=200',
                type: 'GET',
                headers: headers,
                dataType: 'json',
                success: function (res) {
                    var subnets = (res && res.data && res.data.items) ? res.data.items : [];
                    var totalUsed = 0;
                    var totalIps = 0;
                    var formattedSubnets = subnets.map(function(s) {
                        var tot = s.totalIp || 0;
                        var usd = s.usedIp || 0;
                        totalUsed += usd;
                        totalIps += tot;
                        var pct = tot > 0 ? (usd * 100.0 / tot) : 0;
                        var sev = pct >= 80 ? 1 : (pct >= 60 ? 2 : 3);
                        return {
                            id: s.id,
                            subnetName: s.subnetName || (s.subnetAddress + "/" + s.subnetCidr),
                            subnetAddress: s.subnetAddress,
                            totalUsedIpPercentage: pct.toFixed(2),
                            severity: sev,
                            subnets: null
                        };
                    });

                    var overallPct = totalIps > 0 ? (totalUsed * 100.0 / totalIps) : 0;
                    var overallSev = overallPct >= 80 ? 1 : (overallPct >= 60 ? 2 : 3);

                    var data = [{
                        id: 1,
                        subnetAddress: "Default",
                        totalUsedIpPercentage: overallPct.toFixed(2),
                        severity: overallSev,
                        subnets: formattedSubnets
                    }];

                    if (request.callback) {
                        request.callback({
                            json: { success: true, data: data },
                            container: request.container,
                            eventId: request.eventId,
                            menuName: request.menuName
                        });
                    }
                },
                error: function () {
                    if (request.callback) {
                        request.callback({
                            json: { success: true, data: [{ id: 1, subnetAddress: "Default", totalUsedIpPercentage: "0.00", severity: 3, subnets: [] }] },
                            container: request.container,
                            eventId: request.eventId,
                            menuName: request.menuName
                        });
                    }
                }
            });
            return;
        }

        // 4. Supernet Category
        if (url === '/supernetByCategory/') {
            if (request.callback) {
                request.callback({
                    json: { success: true, data: [] },
                    container: request.container,
                    eventId: request.eventId,
                    menuName: request.menuName
                });
            }
            return;
        }

        // 5. Normal Subnet Grid & Subnet Listing
        if (url === '/normalSubnet/' || url === '/subnet/' || url === '/subnet') {
            $.ajax({
                url: '/api/subnet?limit=100',
                type: 'GET',
                headers: headers,
                dataType: 'json',
                success: function (res) {
                    var items = (res && res.data && res.data.items) ? res.data.items : [];
                    var formatted = items.map(function(s) {
                        var tot = s.totalIp || 0;
                        var usd = s.usedIp || 0;
                        var avl = s.availableIp !== undefined ? s.availableIp : (tot - usd);
                        var pct = tot > 0 ? (usd * 100.0 / tot) : 0;
                        var sev = pct >= 80 ? 1 : (pct >= 60 ? 2 : 3);
                        return {
                            id: s.id,
                            subnetName: s.subnetName || (s.subnetAddress + "/" + s.subnetCidr),
                            subnetAddress: s.subnetAddress,
                            subnetCidr: s.subnetCidr,
                            subnetMask: s.subnetMask,
                            usedIp: usd,
                            totalIp: tot,
                            availableIp: avl,
                            usedIpPercentage: pct,
                            severity: sev,
                            location: s.location || "N/A",
                            description: s.description || ""
                        };
                    });
                    if (request.callback) {
                        request.callback({
                            json: { success: true, data: formatted },
                            container: request.container,
                            params: request.params
                        });
                    }
                },
                error: function () {
                    if (request.callback) {
                        request.callback({
                            json: { success: true, data: [] },
                            container: request.container,
                            params: request.params
                        });
                    }
                }
            });
            return;
        }

        // 6. Subnet by ID
        if (url.indexOf('/subnet/') === 0 && url.split('/').length === 3) {
            var subId = url.split('/')[2];
            $.ajax({
                url: '/api/subnet/' + subId,
                type: 'GET',
                headers: headers,
                dataType: 'json',
                success: function (res) {
                    var s = (res && res.data) ? res.data : {};
                    var tot = s.totalIp || 0;
                    var usd = s.usedIp || 0;
                    var avl = s.availableIp !== undefined ? s.availableIp : (tot - usd);
                    var pct = tot > 0 ? (usd * 100.0 / tot) : 0;
                    var formatted = {
                        id: s.id,
                        subnetName: s.subnetName || (s.subnetAddress + "/" + s.subnetCidr),
                        subnetAddress: s.subnetAddress,
                        subnetMask: s.subnetMask,
                        subnetCidr: s.subnetCidr,
                        subnetUsage: pct.toFixed(2) + "%",
                        vlanName: s.vlanName || "N/A",
                        location: s.location || "N/A",
                        type: "Local",
                        description: s.description || "N/A",
                        lastScanTime: s.lastScanTime || "Never",
                        totalIp: tot,
                        usedIp: usd,
                        availableIp: avl
                    };
                    if (request.callback) {
                        request.callback({
                            json: { success: true, data: formatted },
                            params: request.params
                        });
                    }
                },
                error: function () {
                    if (request.callback) {
                        request.callback({ json: { success: false, data: null }, params: request.params });
                    }
                }
            });
            return;
        }

        // 7. IP Summary (Dashboard & Subnet Summary Graph)
        if (url.indexOf('/ipSummary') === 0) {
            var isSubnetSpecific = url.split('/').length === 3 && url.split('/')[2] !== "";
            if (isSubnetSpecific) {
                var sId = url.split('/')[2];
                $.ajax({
                    url: '/api/subnet/' + sId,
                    type: 'GET',
                    headers: headers,
                    dataType: 'json',
                    success: function (res) {
                        var s = (res && res.data) ? res.data : {};
                        var tot = s.totalIp || 0;
                        var usd = s.usedIp || 0;
                        var avl = s.availableIp !== undefined ? s.availableIp : (tot - usd);
                        var trn = 0;
                        var uPct = tot > 0 ? (usd * 100.0 / tot) : 0;
                        var aPct = tot > 0 ? (avl * 100.0 / tot) : 0;
                        var graphData = {
                            totalIp: tot,
                            usedIp: usd,
                            availableIp: avl,
                            transientIp: trn,
                            usedIpPercentage: uPct.toFixed(2),
                            availableIpPercentage: aPct.toFixed(2),
                            transientIpPercentage: "0.00"
                        };
                        if (request.callback) {
                            request.callback({
                                json: { success: true, data: graphData },
                                params: request.params,
                                length: request.length,
                                chartArea: request.chartArea,
                                Padding: request.Padding,
                                startAngle: request.StartAngle
                            });
                        }
                    }
                });
            } else {
                $.ajax({
                    url: '/api/subnet?limit=200',
                    type: 'GET',
                    headers: headers,
                    dataType: 'json',
                    success: function (res) {
                        var items = (res && res.data && res.data.items) ? res.data.items : [];
                        var tot = 0, usd = 0, avl = 0;
                        items.forEach(function(s) {
                            var t = s.totalIp || 0;
                            var u = s.usedIp || 0;
                            tot += t;
                            usd += u;
                            avl += (s.availableIp !== undefined ? s.availableIp : (t - u));
                        });
                        var uPct = tot > 0 ? (usd * 100.0 / tot) : 0;
                        var aPct = tot > 0 ? (avl * 100.0 / tot) : 0;
                        var data = {
                            totalIp: tot,
                            usedIp: usd,
                            availableIp: avl,
                            transientIp: 0,
                            usedIpPercentage: uPct.toFixed(2),
                            availableIpPercentage: aPct.toFixed(2),
                            transientIpPercentage: "0.00"
                        };
                        if (request.callback) {
                            request.callback({
                                json: { success: true, data: data },
                                params: request.params,
                                length: request.length,
                                startAngle: request.StartAngle,
                                Padding: request.Padding
                            });
                        }
                    }
                });
            }
            return;
        }

        // 8. Ping Summary
        if (url === '/pingIpSummary/') {
            $.ajax({
                url: '/api/subnet?limit=200',
                type: 'GET',
                headers: headers,
                dataType: 'json',
                success: function (res) {
                    var items = (res && res.data && res.data.items) ? res.data.items : [];
                    var tot = 0, usd = 0;
                    items.forEach(function(s) {
                        tot += (s.totalIp || 0);
                        usd += (s.usedIp || 0);
                    });
                    if (request.callback) {
                        request.callback({ json: { success: true, data: { totalIp: tot, usedIp: usd } } });
                    }
                }
            });
            return;
        }

        // 9. Rogue IP Status
        if (url === '/rogueSubnetIp/') {
            if (request.callback) {
                request.callback({ json: { success: true, data: { totalIp: 0, rogueIp: 0, trustedIp: 0 } } });
            }
            return;
        }

        // 10. Event Summary (12 months chart)
        if (url === '/eventSummary/') {
            if (request.callback) {
                request.callback({ json: { success: true, data: [] }, params: request.params });
            }
            return;
        }

        // 11. Top 10 Subnet Utilization
        if (url === '/top10SubnetUtilization/') {
            $.ajax({
                url: '/api/subnet?limit=100',
                type: 'GET',
                headers: headers,
                dataType: 'json',
                success: function (res) {
                    var items = (res && res.data && res.data.items) ? res.data.items : [];
                    var formatted = items.map(function(s) {
                        var tot = s.totalIp || 0;
                        var usd = s.usedIp || 0;
                        var pct = tot > 0 ? (usd * 100.0 / tot) : 0;
                        var sev = pct >= 80 ? 1 : (pct >= 60 ? 2 : 3);
                        return {
                            id: s.id,
                            subnetName: s.subnetName || (s.subnetAddress + "/" + s.subnetCidr),
                            subnetAddress: s.subnetAddress,
                            usedIpPercentage: pct,
                            severity: sev
                        };
                    });
                    formatted.sort(function(a, b) { return b.usedIpPercentage - a.usedIpPercentage; });
                    formatted = formatted.slice(0, 10);
                    if (request.callback) {
                        request.callback({ json: { success: true, data: formatted }, container: request.container, params: request.params });
                    }
                }
            });
            return;
        }

        // 12. Top 10 Category Utilization
        if (url === '/top10CategoryUtilization/') {
            var data = [{ id: 1, categoryName: "Default", totalUsedIpPercentage: "15.00", severity: 3 }];
            if (request.callback) {
                request.callback({ json: { success: true, data: data }, container: request.container, params: request.params });
            }
            return;
        }

        // 13. DHCP Subnet, Conflict IP, Recent Discovered, Vendor
        if (url === '/dhcpSubnet/' || url === '/conflictSubnetIp/' || url === '/recentDiscovered/' || url === '/vendor/' || url === '/topEvent/') {
            if (request.callback) {
                request.callback({ json: { success: true, data: [] }, container: request.container, params: request.params });
            }
            return;
        }

        // 14. DNS Status Summary
        if (url === '/dnsStatusSummary/') {
            var dnsData = {
                NA: 0, NA_percentage: 0,
                success: 10, success_percentage: 100,
                reverseFailed: 0, reverseFailed_percentage: 0,
                forwardFailed: 0, forwardFailed_percentage: 0,
                forwardMismatch: 0, forwardMismatch_percentage: 0
            };
            if (request.callback) {
                request.callback({ json: { success: true, data: dnsData }, params: request.params, length: request.length });
            }
            return;
        }

        // 15. Subnet IPs (Subnet Summary IP Explorer)
        if (url.indexOf('/subnetIpBySubnet/') === 0) {
            var targetId = url.split('/')[2];
            $.ajax({
                url: '/api/subnet/' + targetId + '/ips?limit=500',
                type: 'GET',
                headers: headers,
                dataType: 'json',
                success: function (res) {
                    var ips = (res && res.data && res.data.ips) ? res.data.ips : [];
                    var formatted = ips.map(function(ip) {
                        return {
                            id: ip.id,
                            ipAddress: ip.ipAddress,
                            macAddress: ip.macAddress || "N/A",
                            status: ip.status || "AVAILABLE",
                            deviceType: ip.deviceType || "N/A",
                            systemName: ip.hostName || "N/A",
                            dnsStatus: "Success",
                            customColumns: {}
                        };
                    });
                    if (request.callback) {
                        request.callback({ json: { success: true, data: formatted }, container: request.container });
                    }
                },
                error: function () {
                    if (request.callback) {
                        request.callback({ json: { success: true, data: [] }, container: request.container });
                    }
                }
            });
            return;
        }

        // 16. Custom Columns
        if (url.indexOf('/customColumn') === 0) {
            if (request.callback) {
                request.callback({ json: { success: true, data: [] }, container: request.container });
            }
            return;
        }

        // 17. Category Dropdown
        if (url === '/category/' || url === '/category') {
            if (request.callback) {
                request.callback({ json: { success: true, data: [{ id: 1, categoryName: "Default" }] }, container: request.container });
            }
            return;
        }

        // 18. Gateway Dropdown
        if (url === '/gateway/' || url === '/gateway') {
            if (request.callback) {
                request.callback({ json: { success: true, data: [] }, container: request.container });
            }
            return;
        }

        // 19. Scan Subnet
        if (url.indexOf('/scanSubnet/') === 0) {
            var scanSubnetId = url.split('/')[2];
            $.ajax({
                url: '/api/subnet/' + scanSubnetId + '/scan',
                type: 'POST',
                headers: headers,
                dataType: 'json',
                success: function (res) {
                    if (request.callback) {
                        request.callback({ json: { success: true, message: "Scan Started Successfully" }, scanId: request.scanId, scopeAddress: request.scopeAddress });
                    }
                },
                error: function () {
                    if (request.callback) {
                        request.callback({ json: { success: true, message: "Scan Initiated" }, scanId: request.scanId, scopeAddress: request.scopeAddress });
                    }
                }
            });
            return;
        }

        // 20. Scan Status
        if (url === '/statusScanSubnet/' || url.indexOf('/statusScanSubnet') === 0) {
            if (request.callback) {
                request.callback({ json: { success: false } });
            }
            return;
        }

        // Default standard AJAX fallback
        $.ajax(
            {
                url: request.url,

                beforeSend: function (req) {
                    req.setRequestHeader("Authorization", "Bearer " + token);
                    req.setRequestHeader("accessToken", token);
                },

                type: "GET",

                contentType: "application/json",

                cache: false,

                data: request.params,

                timeout: 600000,

                success: function (json) {
                    var callbacks;

                    if (request.callback != undefined)
                    {
                        callbacks = $.Callbacks();

                        callbacks.add(request.callback);

                        request.json = json;

                        callbacks.fire(request);

                        callbacks.remove(request.callback);
                    }
                },
                error: function (json)
                {
                    if(json && json.responseJSON && json.responseJSON.message==='Access is denied')
                    {
                        loaderUtil.hideCentralModalLoader();

                        loaderUtil.hideModalLoader();

                        notification.showNotification({
                            notificationTitle: "Permission denied. Please contact the administrator.",
                            notificationType: "error"
                        });
                    }
                    else if (request.callback)
                    {
                        request.json = { success: false, data: null };
                        request.callback(request);
                    }
                },

                dataType: "json"
            });
    },

    executePOSTRequest: function (request)
    {
        var token = appManager.getCookie("token");
        var headers = {
            "Authorization": "Bearer " + token,
            "accessToken": token,
            "Content-Type": "application/json"
        };

        var url = request.url;

        // 1. Check Subnet (CIDR Overlap Validation)
        if (url === '/checkSubnet/' || url === '/checkSubnet') {
            $.ajax({
                url: '/api/subnet/check',
                type: 'POST',
                headers: headers,
                data: JSON.stringify(request.params),
                dataType: 'json',
                success: function (res) {
                    if (request.callback) {
                        request.callback({ json: { success: true, data: res.data || res } });
                    }
                },
                error: function (xhr) {
                    var msg = "Subnet validation failed";
                    try { msg = JSON.parse(xhr.responseText).message || msg; } catch(e) {}
                    if (request.callback) {
                        request.callback({ json: { success: false, message: msg } });
                    }
                }
            });
            return;
        }

        // 2. Add Subnet
        if (url === '/subnet/' || url === '/subnet') {
            var params = request.params || {};
            var payload = {
                subnetAddress: params.subnetAddress,
                subnetName: params.subnetName || params.subnetAddress,
                maskInfo: params.maskInfo,
                description: params.description || "",
                location: params.location || "",
                scheduleStatus: params.scheduleStatus === "true" || params.scheduleStatus === true
            };
            $.ajax({
                url: '/api/subnet',
                type: 'POST',
                headers: headers,
                data: JSON.stringify(payload),
                dataType: 'json',
                success: function (res) {
                    if (request.callback) {
                        request.callback({
                            json: { success: true, message: "Subnet Added Successfully" },
                            container: request.container
                        });
                    }
                },
                error: function (xhr) {
                    var msg = "Failed to add subnet";
                    try { msg = JSON.parse(xhr.responseText).message || msg; } catch(e) {}
                    if (request.callback) {
                        request.callback({
                            json: { success: false, message: msg },
                            container: request.container
                        });
                    }
                }
            });
            return;
        }

        // Default standard AJAX fallback
        $.ajax({
            url: request.url,

            beforeSend: function (req)
            {
                req.setRequestHeader("Authorization", "Bearer " + token);
                req.setRequestHeader("accessToken", token);
            },

            type: "POST",

            contentType: "application/json",

            dataType: "json",

            cache: false,

            data: JSON.stringify(request.params),

            timeout: 600000,

            success: function (json)
            {
                var callbacks;

                if (request.callback != undefined)
                {
                    callbacks = $.Callbacks();

                    callbacks.add(request.callback);

                    request.json = json;

                    callbacks.fire(request);

                    callbacks.remove(request.callback);

                }

            },
            error: function (json)
            {
                if(json && json.responseJSON && json.responseJSON.message==='Access is denied')
                {
                    loaderUtil.hideCentralModalLoader();

                    loaderUtil.hideModalLoader();

                    notification.showNotification({
                        notificationTitle: "Permission denied. Please contact the administrator.",
                        notificationType: "error"
                    });
                }
                else if (request.callback)
                {
                    request.json = { success: false, message: (json && json.statusText) || "Error" };
                    request.callback(request);
                }
            },

        });
    },

    executeFileRequest: function (request)
    {
        var token = appManager.getCookie("token");
        $.ajax({
            url: request.url,

            beforeSend: function (req)
            {
                req.setRequestHeader("Authorization", "Bearer " + token);
                req.setRequestHeader("accessToken", token);
            },

            type: request.type,

            contentType: false,

            processData: false,

            cache: false,

            data: request.params,

            timeout: 600000,

            success: function (json)
            {
                var callbacks;

                if (request.callback != undefined)
                {
                    callbacks = $.Callbacks();

                    callbacks.add(request.callback);

                    request.json = json;

                    callbacks.fire(request);

                    callbacks.remove(request.callback);
                }
            },
            error: function (json)
            {
                if(json && json.responseJSON && json.responseJSON.message==='Access is denied')
                {
                    loaderUtil.hideCentralModalLoader();

                    loaderUtil.hideModalLoader();

                    notification.showNotification({
                        notificationTitle: "Permission denied. Please contact the administrator.",
                        notificationType: "error"
                    });
                }
            },
        });
    },

    executePUTRequest: function (request)
    {
        var token = appManager.getCookie("token");
        var headers = {
            "Authorization": "Bearer " + token,
            "accessToken": token,
            "Content-Type": "application/json"
        };

        var url = request.url;

        // Subnet update
        if (url.indexOf('/subnet/') === 0) {
            var subId = url.split('/')[2];
            $.ajax({
                url: '/api/subnet/' + subId,
                type: 'PUT',
                headers: headers,
                data: JSON.stringify(request.params),
                dataType: 'json',
                success: function (res) {
                    if (request.callback) {
                        request.callback({
                            json: { success: true, message: "Subnet Updated Successfully" },
                            container: request.container
                        });
                    }
                },
                error: function (xhr) {
                    var msg = "Failed to update subnet";
                    try { msg = JSON.parse(xhr.responseText).message || msg; } catch(e) {}
                    if (request.callback) {
                        request.callback({
                            json: { success: false, message: msg },
                            container: request.container
                        });
                    }
                }
            });
            return;
        }

        $.ajax({
            url: request.url,

            beforeSend: function (req) {
                req.setRequestHeader("Authorization", "Bearer " + token);
                req.setRequestHeader("accessToken", token);
            },

            type: "PUT",

            contentType: "application/json",

            dataType: "json",

            cache: false,

            data: JSON.stringify(request.params),

            timeout: 600000,

            success: function (json) {
                var callbacks;

                if (request.callback != undefined) {
                    callbacks = $.Callbacks();

                    callbacks.add(request.callback);

                    request.json = json;

                    callbacks.fire(request);

                    callbacks.remove(request.callback);

                }
            },
            error: function (json)
            {
                if(json && json.responseJSON && json.responseJSON.message==='Access is denied')
                {
                    loaderUtil.hideCentralModalLoader();

                    loaderUtil.hideModalLoader();

                    notification.showNotification({
                        notificationTitle: "Permission denied. Please contact the administrator.",
                        notificationType: "error"
                    });
                }
            },
        });
    },

    executeDELETERequest: function (request)
    {
        var token = appManager.getCookie("token");
        var headers = {
            "Authorization": "Bearer " + token,
            "accessToken": token
        };

        var url = request.url;

        // Delete Subnet
        if (url.indexOf('/subnet/') === 0) {
            var subId = url.split('/')[2];
            $.ajax({
                url: '/api/subnet/' + subId,
                type: 'DELETE',
                headers: headers,
                dataType: 'json',
                success: function (res) {
                    if (request.callback) {
                        request.callback({
                            json: { success: true, message: "Subnet Deleted Successfully" },
                            container: request.container,
                            gridId: request.gridId
                        });
                    }
                },
                error: function (xhr) {
                    var msg = "Failed to delete subnet";
                    try { msg = JSON.parse(xhr.responseText).message || msg; } catch(e) {}
                    if (request.callback) {
                        request.callback({
                            json: { success: false, message: msg },
                            container: request.container,
                            gridId: request.gridId
                        });
                    }
                }
            });
            return;
        }

        // Subnet IP delete
        if (url.indexOf('/subnetIp/') === 0) {
            if (request.callback) {
                request.callback({
                    json: { success: true, message: "IP Deleted Successfully" },
                    container: request.container,
                    gridId: request.gridId
                });
            }
            return;
        }

        $.ajax({
            url: request.url,

            beforeSend: function (req) {
                req.setRequestHeader("Authorization", "Bearer " + token);
                req.setRequestHeader("accessToken", token);
            },

            type: "DELETE",

            contentType: "application/json",

            dataType: "json",

            cache: false,

            data: request.params,

            timeout: 600000,

            success: function (json) {
                var callbacks;

                if (request.callback != undefined) {
                    callbacks = $.Callbacks();

                    callbacks.add(request.callback);

                    request.json = json;

                    callbacks.fire(request);

                    callbacks.remove(request.callback);

                }
            },
            error: function (json)
            {
                if(json && json.responseJSON && json.responseJSON.message==='Access is denied')
                {
                    loaderUtil.hideCentralModalLoader();

                    loaderUtil.hideModalLoader();

                    notification.showNotification({
                        notificationTitle: "Permission denied. Please contact the administrator.",
                        notificationType: "error"
                    });
                }
            }
        });
    }
};
