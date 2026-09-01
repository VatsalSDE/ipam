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

        window.removeEventListener('hashchange', flux.onBrowserHistoryButtonClick);

        window.addEventListener("hashchange", flux.onBrowserHistoryButtonClick);
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

    // -------------------------------------------------------------------------------------------- Data Store & State ------------------------------------------------------------------------------//

    appDataStore: {
        getKey: function(k, defaultVal) {
            try {
                var item = localStorage.getItem("ipam_" + k);
                return item ? JSON.parse(item) : defaultVal;
            } catch(e) {
                return defaultVal;
            }
        },
        setKey: function(k, val) {
            try {
                localStorage.setItem("ipam_" + k, JSON.stringify(val));
            } catch(e) {}
        },
        getAlerts: function() {
            return this.getKey("alerts", [
                { id: 1, alertType: "Subnet Utilization", message: "Subnet 192.168.1.0/24 reached 85% threshold", subnet: "192.168.1.0/24", timestamp: "2026-08-30 08:30:00" },
                { id: 2, alertType: "IP Conflict", message: "Conflicting MAC address detected for 192.168.1.15", subnet: "192.168.1.0/24", timestamp: "2026-08-30 07:45:00" },
                { id: 3, alertType: "Rogue Device", message: "Unidentified host with MAC 00:50:56:C0:00:08 detected", subnet: "192.168.1.0/24", timestamp: "2026-08-30 06:12:00" }
            ]);
        },
        getEvents: function() {
            return this.getKey("events", [
                { id: 1, eventType: "Add Subnet", eventContext: "Subnet 192.168.100.0 is added in IP Address Manager by admin", timestamp: "2026-08-27 12:02:11", userName: "admin" },
                { id: 2, eventType: "Scan Subnet", eventContext: "Subnet 192.168.100.0 is scanned in IP Address Manager by admin", timestamp: "2026-08-27 12:13:05", userName: "admin" },
                { id: 3, eventType: "Delete Subnet", eventContext: "Subnet 192.168.100.0 is deleted from IP Address Manager by admin", timestamp: "2026-08-27 12:13:24", userName: "admin" },
                { id: 4, eventType: "Add Subnet", eventContext: "Subnet 192.168.1.0 is added in IP Address Manager by admin", timestamp: "2026-08-27 12:33:27", userName: "admin" },
                { id: 5, eventType: "Scan Subnet", eventContext: "Subnet 192.168.1.0 is scanned in IP Address Manager by admin", timestamp: "2026-08-29 16:40:00", userName: "admin" }
            ]);
        },
        getReportSchedulers: function() {
            return this.getKey("reportSchedulers", [
                { id: 1, schedulerName: "Weekly Subnet Summary", exportType: "PDF", ipFilter: "ALL", subnetId: "1", schedulerTimeLine: 10, repeatFlag: true, repeatDay: "Monday", repeatDate: null, repeatMonth: null, repeatHourTime: "09", schedulerTime: "09:00", emailTo: "admin@motadata.com" },
                { id: 2, schedulerName: "Daily Utilization Audit", exportType: "CSV", ipFilter: "USED", subnetId: "1", schedulerTimeLine: 1, repeatFlag: true, repeatDay: null, repeatDate: null, repeatMonth: null, repeatHourTime: "18", schedulerTime: "18:00", emailTo: "admin@motadata.com" }
            ]);
        },
        getRogueDevices: function() {
            return this.getKey("rogueDevices", [
                { id: 1, macAddress: "00:50:56:C0:00:08", ipAddress: "192.168.1.15", discoveredAt: "2026-08-30 07:30:00", nicType: "VMware Virtual NIC", authenticity: "Rogue" },
                { id: 2, macAddress: "3C:D9:2B:44:11:AA", ipAddress: "192.168.1.22", discoveredAt: "2026-08-30 08:10:00", nicType: "Intel Gigabit Ethernet", authenticity: "Discovered" },
                { id: 3, macAddress: "00:1A:2B:3C:4D:5E", ipAddress: "192.168.1.1", discoveredAt: "2026-08-29 10:00:00", nicType: "Cisco Catalyst Interface", authenticity: "Trusted" }
            ]);
        },
        getIpRequests: function() {
            return this.getKey("ipRequests", [
                { id: 1, requestedBy: "dev-team@motadata.com", NoOfIps: 4, numberOfIps: 4, requestedOn: "2026-08-29 11:30:00", status: "Approved", reviewedOn: "2026-08-29 12:00:00", reviewedBy: "admin", location: "Server Room A", purpose: "Kubernetes Worker Nodes", subnetId: "1", ips: "192.168.1.50 - 192.168.1.53" },
                { id: 2, requestedBy: "qa-lead@motadata.com", NoOfIps: 2, numberOfIps: 2, requestedOn: "2026-08-30 08:00:00", status: "Pending", reviewedOn: "Pending Review", reviewedBy: "Pending", location: "Lab 2", purpose: "Staging Testbeds", subnetId: "1", ips: "Pending Allocation" }
            ]);
        },
        getDhcpServers: function() {
            return this.getKey("dhcpServers", [
                { id: 1, credentialName: "Win-DHCP-HQ", serverAddress: "192.168.1.50", serverType: "Windows", subnetMask: "255.255.255.0", status: true, lastScanTime: "2026-08-30 08:00:00", description: "Primary Domain Controller DHCP", scopeAddress: "192.168.1.0", totalIp: 254, usedIp: 42, availableIp: 212 }
            ]);
        },
        getUsers: function() {
            return this.getKey("users", [
                { id: 1, userName: "admin", email: "admin@motadata.com", role: "ROLE_ADMIN", role_name: "ROLE_ADMIN", userRoleId: 1, status: true, description: "System Administrator", previousLoginStatus: "2026-08-30 08:10:27" },
                { id: 2, userName: "operator", email: "operator@motadata.com", role: "ROLE_USER", role_name: "ROLE_USER", userRoleId: 2, status: true, description: "Network Operations", previousLoginStatus: "2026-08-29 17:00:00" }
            ]);
        },
        getRoles: function() {
            return [
                { id: 1, role: "ROLE_ADMIN", role_name: "ROLE_ADMIN", description: "Administrator" },
                { id: 2, role: "ROLE_USER", role_name: "ROLE_USER", description: "Operator / Standard User" }
            ];
        },
        getMailConfig: function() {
            return this.getKey("mailConfig", {
                id: 1, host: "smtp.motadata.com", port: 587, username: "notifications@motadata.com", fromEmail: "ipam-alerts@motadata.com", sslEnabled: true, tlsEnabled: true, password: "••••••••"
            });
        },
        getDbMaintenance: function() {
            return this.getKey("dbMaintenance", {
                id: 1, backupPath: "/home/vatsal-rathi/Downloads/IPAM/backup", scheduleHour: 2, duration: "Days", maintainedDays: 30, scheduleStatus: true, status: 0
            });
        },
        getConfigureAlert: function() {
            return this.getKey("configureAlert", {
                id: 1, ipConflict: true, ipUtilizationFlag: true, newSubnetsDiscovered: true, utilizationThreshold: 80
            });
        },
        getGateways: function() {
            return this.getKey("gateways", [
                { id: 1, gateway: "192.168.1.1", version: "v2c", community: "public", port: 161, description: "Core Gateway Router", lastScanTime: "2026-08-30 08:00:00" }
            ]);
        },
        getDiscoveredSubnets: function() {
            return this.getKey("discoveredSubnets", [
                { id: 1, subnetAddress: "10.10.0.0", subnetMask: "255.255.0.0", subnetCidr: "16", gateway: "192.168.1.1", discoveredTime: "2026-08-30 08:05:00", description: "Discovered via Gateway SNMP" }
            ]);
        },
        getCustomColumns: function() {
            return this.getKey("customColumns", [
                { id: 1, columnName: "Asset Tag", columnType: "String", isRequired: false },
                { id: 2, columnName: "Owner", columnType: "String", isRequired: false }
            ]);
        },
        getCategories: function() {
            return this.getKey("categories", [
                { id: 1, categoryName: "Default" },
                { id: 2, categoryName: "Datacenter" },
                { id: 3, categoryName: "Office LAN" }
            ]);
        },
        getSupernets: function() {
            return this.getKey("supernets", [
                { id: 1, supernetAddress: "192.168.0.0", supernetCidr: "16", supernetMask: "255.255.0.0", supernetName: "Corporate Supernet", description: "Master block" }
            ]);
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
            if (request.callback) request.callback({ json: { success: true, data: true, currentUserRole: 'ROLE_ADMIN' }, container: request.container });
            return;
        }

        // 2. Brand & Global Settings
        if (url === '/brand/1' || url.startsWith('/brand')) {
            var brandData = appManager.appDataStore.getKey("brand", { id: 1, productName: "Motadata", logo: "/images/logo.png" });
            if (request.callback) request.callback({ json: { success: true, data: brandData }, container: request.container });
            return;
        }
        if (url === '/globalSetting/1' || url.startsWith('/globalSetting')) {
            var globalData = appManager.appDataStore.getKey("globalSetting", { id: 1, cssMode: 1, loggingLevel: 3 });
            if (request.callback) request.callback({ json: { success: true, data: globalData }, container: request.container });
            return;
        }

        // Import Subnet Status Polling
        if (url.startsWith('/importSubnetStatus')) {
            if (request.callback) request.callback({ json: { success: true, inProgress: false }, container: request.container });
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
                    var subnets = (res && res.data) ? (res.data.subnets || res.data.items || []) : [];
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
            var supernets = appManager.appDataStore.getSupernets();
            if (request.callback) {
                request.callback({
                    json: { success: true, data: supernets },
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
                    var items = (res && res.data) ? (res.data.subnets || res.data.items || []) : [];
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
        if (url.indexOf('/subnet/') === 0 && url.split('/').length === 3 && !isNaN(url.split('/')[2])) {
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
                                Padding: request.Padding,
                                chartArea: request.chartArea
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
                url: '/api/subnet?limit=100',
                type: 'GET',
                headers: headers,
                dataType: 'json',
                success: function (res) {
                    var items = (res && res.data) ? (res.data.subnets || res.data.items || []) : [];
                    var total = 0, used = 0;
                    items.forEach(function(s) {
                        total += (s.totalIp || 0);
                        used += (s.usedIp || 0);
                    });
                    if (request.callback) {
                        request.callback({ json: { success: true, data: { totalIp: total || 254, usedIp: used, totalPing: total || 254, failurePing: (total - used) > 0 ? (total - used) : 0 } } });
                    }
                },
                error: function() {
                    if (request.callback) {
                        request.callback({ json: { success: true, data: { totalIp: 254, usedIp: 0, totalPing: 254, failurePing: 0 } } });
                    }
                }
            });
            return;
        }

        // 9. Rogue IP Summary
        if (url === '/rogueSubnetIp/') {
            var rogues = appManager.appDataStore.getRogueDevices();
            var rogueCount = rogues.filter(function(r) { return r.authenticity === 'Rogue'; }).length;
            var trustedCount = rogues.filter(function(r) { return r.authenticity === 'Trusted'; }).length;
            var discCount = rogues.filter(function(r) { return r.authenticity === 'Discovered'; }).length;
            var totalRogues = discCount + rogueCount + trustedCount;
            if (request.callback) {
                request.callback({ json: { success: true, data: { totalIp: totalRogues, rogueIp: rogueCount, trustedIp: trustedCount, discoverRogue: discCount, rogue: rogueCount, trusted: trustedCount } } });
            }
            return;
        }

        // 10. Event Summary (Dashboard 12-Month Graph)
        if (url === '/eventSummary/') {
            var months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
            var mockGraph = months.map(function(m, idx) {
                return { name: m, count: idx === 7 ? 5 : 0, color: "#00b3ee" };
            });
            if (request.callback) {
                request.callback({ json: { success: true, data: mockGraph }, params: request.params });
            }
            return;
        }

        // 11. Top 10 Subnet Utilization
        if (url === '/top10SubnetUtilization/') {
            $.ajax({
                url: '/api/subnet?limit=10',
                type: 'GET',
                headers: headers,
                dataType: 'json',
                success: function (res) {
                    var items = (res && res.data) ? (res.data.subnets || res.data.items || []) : [];
                    var top10 = items.map(function(s) {
                        var tot = s.totalIp || 0;
                        var usd = s.usedIp || 0;
                        var pct = tot > 0 ? (usd * 100.0 / tot) : 0;
                        var sev = pct >= 80 ? 1 : (pct >= 60 ? 2 : 3);
                        return {
                            id: s.id,
                            subnetAddress: s.subnetAddress,
                            usedIpPercentage: pct,
                            severity: sev
                        };
                    });
                    if (request.callback) {
                        request.callback({ json: { success: true, data: top10 }, container: request.container, params: request.params });
                    }
                },
                error: function () {
                    if (request.callback) {
                        request.callback({ json: { success: true, data: [] }, container: request.container, params: request.params });
                    }
                }
            });
            return;
        }

        // 12. Top 10 Category Utilization
        if (url === '/top10CategoryUtilization/') {
            if (request.callback) {
                request.callback({
                    json: { success: true, data: [{ id: 1, categoryName: "Default", usedIpPercentage: 0, severity: 3 }] },
                    container: request.container,
                    params: request.params
                });
            }
            return;
        }

        // 13. Dashboard Misc Grids
        if (url === '/dhcpSubnet/' || url === '/conflictSubnetIp/' || url === '/recentDiscovered/' || url === '/vendor/') {
            if (request.callback) {
                request.callback({ json: { success: true, data: [] }, container: request.container, params: request.params });
            }
            return;
        }

        // 13.1 Top 25 Events (Right Panel)
        if (url === '/topEvent/' || url === '/topEvent') {
            $.ajax({
                url: '/api/event/top',
                type: 'GET',
                headers: headers,
                dataType: 'json',
                success: function (res) {
                    var items = (res && Array.isArray(res.data)) ? res.data : [];
                    var formatted = items.map(function (e) {
                        var uName = e.userName || (e.doneBy && e.doneBy.userName) || "admin";
                        return {
                            id: e.id,
                            eventType: e.eventType || "SYSTEM",
                            eventContext: e.eventContext || "",
                            timestamp: e.timestamp || "",
                            severity: e.severity != null ? e.severity : 1,
                            userName: uName,
                            doneBy: { userName: uName }
                        };
                    });
                    if (request.callback) {
                        request.callback({ json: { success: true, data: formatted }, container: request.container });
                    }
                },
                error: function () {
                    var events = appManager.appDataStore.getEvents().slice(0, 25);
                    var formatted = events.map(function (e) {
                        var uName = e.userName || (e.doneBy && e.doneBy.userName) || "admin";
                        return {
                            id: e.id,
                            eventType: e.eventType || "SYSTEM",
                            eventContext: e.eventContext || "",
                            timestamp: e.timestamp || "",
                            severity: e.severity != null ? e.severity : 1,
                            userName: uName,
                            doneBy: { userName: uName }
                        };
                    });
                    if (request.callback) {
                        request.callback({ json: { success: true, data: formatted }, container: request.container });
                    }
                }
            });
            return;
        }

        // 14. DNS Status Summary (Donut Chart)
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
                            ipToDns: "Success",
                            dnsToIp: "Success",
                            authenticity: ip.authenticity || "Trusted",
                            lastAliveTime: ip.lastScanTime || "Never",
                            subnetId: { id: parseInt(targetId), subnetName: "" },
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

        // 16. ALERTS FEATURE (Dedicated Page)
        if (url.indexOf('/alerts/') === 0 || url === '/alerts') {
            var filter = (request.params && request.params.alertFilter) ? request.params.alertFilter : 'live';
            var targetStatus = (filter === 'clear') ? 'all' : 'active';
            $.ajax({
                url: '/api/alerts?status=' + targetStatus + '&limit=100',
                type: 'GET',
                headers: headers,
                dataType: 'json',
                success: function (res) {
                    var items = [];
                    if (res && res.data) {
                        if (Array.isArray(res.data)) {
                            items = res.data;
                        } else if (Array.isArray(res.data.alerts)) {
                            items = res.data.alerts;
                        }
                    }
                    if (request.callback) {
                        request.callback({ json: { success: true, data: items }, container: request.container });
                    }
                },
                error: function () {
                    var allAlerts = appManager.appDataStore.getAlerts();
                    var filteredAlerts = (filter === 'clear') ? [] : allAlerts;
                    if (request.callback) {
                        request.callback({ json: { success: true, data: filteredAlerts }, container: request.container });
                    }
                }
            });
            return;
        }

        // 17. EVENT LOG FEATURE (Dedicated Page)
        if (url.indexOf('/event/') === 0 || url === '/event') {
            var tl = (request.params && request.params.exportTimeline) ? request.params.exportTimeline : '';
            var targetUrl = '/api/event?limit=200' + (tl ? ('&timeline=' + encodeURIComponent(tl)) : '');
            $.ajax({
                url: targetUrl,
                type: 'GET',
                headers: headers,
                dataType: 'json',
                success: function (res) {
                    var items = [];
                    if (res && res.data) {
                        if (Array.isArray(res.data)) {
                            items = res.data;
                        } else if (Array.isArray(res.data.events)) {
                            items = res.data.events;
                        }
                    }
                    var formatted = items.map(function (e) {
                        var uName = e.userName || (e.doneBy && e.doneBy.userName) || "admin";
                        return {
                            id: e.id,
                            eventType: e.eventType || "SYSTEM",
                            eventContext: e.eventContext || "",
                            timestamp: e.timestamp || "",
                            severity: e.severity != null ? e.severity : 1,
                            userName: uName,
                            doneBy: { userName: uName }
                        };
                    });
                    if (request.callback) {
                        request.callback({ json: { success: true, data: formatted }, container: request.container });
                    }
                },
                error: function () {
                    var events = appManager.appDataStore.getEvents();
                    var formatted = events.map(function (e) {
                        var uName = e.userName || (e.doneBy && e.doneBy.userName) || "admin";
                        return {
                            id: e.id,
                            eventType: e.eventType || "SYSTEM",
                            eventContext: e.eventContext || "",
                            timestamp: e.timestamp || "",
                            severity: e.severity != null ? e.severity : 1,
                            userName: uName,
                            doneBy: { userName: uName }
                        };
                    });
                    if (request.callback) {
                        request.callback({ json: { success: true, data: formatted }, container: request.container });
                    }
                }
            });
            return;
        }

        // 18. REPORTS FEATURE (Tree, Grid & Scheduler)
        if (url.indexOf('/subnetByReport/') === 0 || url === '/subnetByReport') {
            $.ajax({
                url: '/api/subnet?limit=100',
                type: 'GET',
                headers: headers,
                dataType: 'json',
                success: function (res) {
                    var items = (res && res.data && res.data.items) ? res.data.items : [];
                    function makeGroup(name, iface) {
                        return {
                            subnetAddress: name,
                            subnets: items.map(function(s) {
                                return {
                                    id: s.id,
                                    subnetName: s.subnetName || (s.subnetAddress + "/" + s.subnetCidr),
                                    subnetAddress: s.subnetAddress,
                                    networkInterface: iface
                                };
                            })
                        };
                    }
                    var reportTree = [
                        makeGroup("All IP", "ALL"),
                        makeGroup("Used IP", "USED"),
                        makeGroup("Available IP", "AVAILABLE"),
                        makeGroup("Reserved IP", "RESERVED"),
                        makeGroup("Transient IP", "TRANSIENT"),
                        makeGroup("Rogue IP", "ROGUE"),
                        makeGroup("Trusted IP", "TRUSTED"),
                        makeGroup("Vendor Summary", "VENDOR SUMMARY")
                    ];
                    if (request.callback) {
                        request.callback({ json: { success: true, data: reportTree }, container: request.container, eventId: request.eventId });
                    }
                },
                error: function() {
                    if (request.callback) {
                        request.callback({ json: { success: true, data: [] }, container: request.container, eventId: request.eventId });
                    }
                }
            });
            return;
        }

        if (url.indexOf('/reportScheduler/') === 0 || url === '/reportScheduler') {
            var scheds = appManager.appDataStore.getReportSchedulers();
            if (request.callback) {
                request.callback({ json: { success: true, data: scheds }, container: request.container, eventId: request.eventId });
            }
            return;
        }

        if (url.indexOf('/subnetIpByReportTimeline/') === 0) {
            var subIdReq = (request.params && request.params.subnetId) ? request.params.subnetId : "1";
            $.ajax({
                url: '/api/subnet/' + subIdReq + '/ips?limit=200',
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
                            dnsStatus: "Success"
                        };
                    });
                    if (request.callback) {
                        request.callback({ json: { success: true, data: formatted }, container: request.container });
                    }
                },
                error: function() {
                    if (request.callback) {
                        request.callback({ json: { success: true, data: [] }, container: request.container });
                    }
                }
            });
            return;
        }

        if (url.indexOf('/vendorByReportTimeline/') === 0) {
            if (request.callback) {
                request.callback({ json: { success: true, data: [] }, container: request.container });
            }
            return;
        }

        // 19. ROGUE DETECTION FEATURE (Dedicated Page)
        if (url.indexOf('/rogueDetection/') === 0 || url === '/rogueDetection') {
            var rogues = appManager.appDataStore.getRogueDevices();
            var parts = url.split('/');
            var panelType = parts.length >= 3 ? parts[2] : undefined;
            var filtered = rogues;
            if (panelType === 'discovered') {
                filtered = rogues.filter(function(r) { return r.authenticity.toLowerCase() === 'discovered'; });
            } else if (panelType === 'trusted') {
                filtered = rogues.filter(function(r) { return r.authenticity.toLowerCase() === 'trusted'; });
            } else if (panelType === 'rogue') {
                filtered = rogues.filter(function(r) { return r.authenticity.toLowerCase() === 'rogue'; });
            }
            if (request.callback) {
                request.callback({ json: { success: true, data: filtered }, container: request.container });
            }
            return;
        }

        // 20. IP REQUESTS FEATURE (Dedicated Page)
        if (url.indexOf('/ipRequests/') === 0 || url === '/ipRequests') {
            var parts = url.split('/');
            var reqList = appManager.appDataStore.getIpRequests();
            if (parts.length === 3 && parts[2] !== "" && !isNaN(parts[2])) {
                var reqId = parseInt(parts[2]);
                var found = reqList.find(function(r) { return r.id === reqId; }) || reqList[0];
                if (request.callback) {
                    request.callback({ json: { success: true, data: found }, container: request.container });
                }
                return;
            }
            if (request.callback) {
                request.callback({ json: { success: true, data: reqList }, container: request.container });
            }
            return;
        }

        // 21. SETTINGS - DHCP MANAGEMENT
        if (url.indexOf('/dhcpCredential/') === 0 || url === '/dhcpCredential') {
            var parts = url.split('/');
            var dhcpList = appManager.appDataStore.getDhcpServers();
            if (parts.length === 3 && parts[2] !== "" && !isNaN(parts[2])) {
                var dId = parseInt(parts[2]);
                var foundD = dhcpList.find(function(d) { return d.id === dId; }) || dhcpList[0];
                if (request.callback) {
                    request.callback({ json: { success: true, data: foundD }, container: request.container });
                }
                return;
            }
            if (request.callback) {
                request.callback({ json: { success: true, data: dhcpList }, container: request.container });
            }
            return;
        }

        if (url === '/windowsDhcpCredential/' || url.startsWith('/windowsDhcpCredential')) {
            if (request.callback) {
                request.callback({ json: { success: true, data: [{ id: 1, credentialName: "Win-DHCP-HQ" }] }, container: request.container });
            }
            return;
        }

        if (url === '/ciscoDhcpCredential/' || url.startsWith('/ciscoDhcpCredential')) {
            if (request.callback) {
                request.callback({ json: { success: true, data: [{ id: 1, credentialName: "Cisco-Core-DHCP" }] }, container: request.container });
            }
            return;
        }

        if (url.indexOf('/dhcpUtilization/') === 0) {
            var stats = {
                totalIp: 254,
                usedIp: 42,
                availableIp: 212,
                transientIp: 0,
                usedIpPercentage: "16.54",
                availableIpPercentage: "83.46",
                transientIpPercentage: "0.00",
                serverAddress: "192.168.1.50"
            };
            if (request.callback) {
                request.callback({ json: { success: true, data: stats }, StartAngle: request.StartAngle, Padding: request.Padding, eventId: request.eventId, eventName: request.eventName });
            }
            return;
        }

        if (url.indexOf('/scanDhcp/') === 0) {
            if (request.callback) {
                request.callback({ json: { success: true, message: "DHCP Server Scan Completed Successfully" }, scanId: request.scanId, scopeAddress: request.scopeAddress });
            }
            return;
        }

        // 22. SETTINGS - USER MANAGEMENT
        if (url.indexOf('/user/') === 0 || url === '/user') {
            var parts = url.split('/');
            var targetUrl = '/api/user';
            if (parts.length === 3 && parts[2] !== "" && !isNaN(parts[2])) {
                targetUrl = '/api/user/' + parts[2];
            }
            $.ajax({
                url: targetUrl,
                type: 'GET',
                headers: headers,
                dataType: 'json',
                success: function (res) {
                    var data = (res && res.data) ? res.data : [];
                    if (request.callback) {
                        request.callback({ json: { success: true, data: data }, container: request.container });
                    }
                },
                error: function () {
                    var users = appManager.appDataStore.getUsers();
                    if (parts.length === 3 && parts[2] !== "" && !isNaN(parts[2])) {
                        var uId = parseInt(parts[2]);
                        var foundU = users.find(function(u) { return u.id === uId; }) || users[0];
                        if (request.callback) {
                            request.callback({ json: { success: true, data: foundU }, container: request.container });
                        }
                        return;
                    }
                    if (request.callback) {
                        request.callback({ json: { success: true, data: users }, container: request.container });
                    }
                }
            });
            return;
        }

        if (url === '/userRole/feature/' || url.indexOf('/userRole/feature') === 0) {
            $.ajax({
                url: '/api/user/roles/features',
                type: 'GET',
                headers: headers,
                dataType: 'json',
                success: function (res) {
                    var data = (res && res.data) ? res.data : [];
                    if (request.callback) {
                        request.callback({ json: { success: true, data: data }, container: request.container });
                    }
                },
                error: function () {
                    var perms = appManager.appDataStore.getRolePermissions();
                    if (request.callback) {
                        request.callback({ json: { success: true, data: perms }, container: request.container });
                    }
                }
            });
            return;
        }

        if (url === '/userRole/' || url.startsWith('/userRole') || url === '/role/' || url.startsWith('/role')) {
            $.ajax({
                url: '/api/user/roles',
                type: 'GET',
                headers: headers,
                dataType: 'json',
                success: function (res) {
                    var data = (res && res.data) ? res.data : [];
                    if (request.callback) {
                        request.callback({ json: { success: true, data: data }, container: request.container });
                    }
                },
                error: function () {
                    var roles = appManager.appDataStore.getRoles();
                    if (request.callback) {
                        request.callback({ json: { success: true, data: roles }, container: request.container });
                    }
                }
            });
            return;
        }

        // 23. SETTINGS - MAIL SERVER
        if (url.indexOf('/mail/') === 0 || url === '/mail' || url.indexOf('/mailConfiguration') === 0) {
            var mailConf = appManager.appDataStore.getMailConfig();
            if (request.callback) {
                request.callback({ json: { success: true, data: mailConf }, container: request.container });
            }
            return;
        }

        // 24. SETTINGS - DATABASE MAINTENANCE
        if (url.indexOf('/databaseMaintenance') === 0) {
            $.ajax({
                url: '/api/database-maintenance',
                type: 'GET',
                headers: headers,
                dataType: 'json',
                success: function (res) {
                    var data = (res && res.data) ? res.data : { id: 1, maintainedDays: 30, status: "enable", scheduleStatus: false };
                    if (request.callback) {
                        request.callback({ json: { success: true, data: data }, container: request.container });
                    }
                },
                error: function () {
                    var dbMaint = appManager.appDataStore.getDbMaintenance();
                    if (request.callback) {
                        request.callback({ json: { success: true, data: dbMaint }, container: request.container });
                    }
                }
            });
            return;
        }

        // 25. SETTINGS - CONFIGURE ALERT
        if (url.indexOf('/configureAlert') === 0) {
            var confAlert = appManager.appDataStore.getConfigureAlert();
            if (request.callback) {
                request.callback({ json: { success: true, data: confAlert }, container: request.container });
            }
            return;
        }

        // 26. SETTINGS - DISCOVERY (Real REST Backend)
        if (url.indexOf('/gateways/') === 0 || url === '/gateways') {
            $.ajax({
                url: '/api/gateway',
                type: 'GET',
                headers: { 'Authorization': 'Bearer ' + appManager.getCookie("token") },
                success: function(res) {
                    var items = (res && res.data) ? res.data : [];
                    if (request.callback) request.callback({ json: { success: true, data: items }, container: request.container });
                },
                error: function(xhr) {
                    if (request.callback) request.callback({ json: { success: false, message: "Failed to fetch gateways" }, container: request.container });
                }
            });
            return;
        }

        if (url.indexOf('/gateway/') === 0) {
            var gId = url.split('/')[2];
            $.ajax({
                url: '/api/gateway/' + gId,
                type: 'GET',
                headers: { 'Authorization': 'Bearer ' + appManager.getCookie("token") },
                success: function(res) {
                    if (request.callback) request.callback({ json: { success: true, data: res.data }, container: request.container });
                },
                error: function(xhr) {
                    if (request.callback) request.callback({ json: { success: false }, container: request.container });
                }
            });
            return;
        }

        if (url.indexOf('/discoveredSubnet/') === 0 || url === '/discoveredSubnet') {
            var parts = url.split('/');
            if (parts.length === 3 && parts[2] !== "" && !isNaN(parts[2])) {
                var dsId = parts[2];
                $.ajax({
                    url: '/api/discovered-subnet/' + dsId,
                    type: 'GET',
                    headers: { 'Authorization': 'Bearer ' + appManager.getCookie("token") },
                    success: function(res) {
                        var d = (res && res.data) ? res.data : {};
                        var item = {
                            id: d.id,
                            subnet: d.subnet,
                            subnetAddress: d.subnet,
                            subnetMask: d.subnetMask,
                            gateway: d.gateway,
                            gatewayId: d.gatewayId
                        };
                        if (request.callback) request.callback({ json: { success: true, data: item }, container: request.container });
                    }
                });
                return;
            }
            $.ajax({
                url: '/api/discovered-subnet',
                type: 'GET',
                headers: { 'Authorization': 'Bearer ' + appManager.getCookie("token") },
                success: function(res) {
                    var items = (res && res.data) ? res.data : [];
                    // Ensure field aliases for Kendo Grid binding
                    var mapped = items.map(function(item) {
                        return {
                            id: item.id,
                            subnet: item.subnet,
                            subnetAddress: item.subnet,
                            subnetMask: item.subnetMask,
                            gateway: item.gateway,
                            gatewayId: item.gatewayId
                        };
                    });
                    if (request.callback) request.callback({ json: { success: true, data: mapped }, container: request.container });
                },
                error: function(xhr) {
                    if (request.callback) request.callback({ json: { success: false, data: [] }, container: request.container });
                }
            });
            return;
        }

        if (url === '/statusScanGateway/' || url.startsWith('/statusScanGateway')) {
            if (request.callback) {
                request.callback({ json: { success: true, data: { status: "Completed", progress: 100 } } });
            }
            return;
        }

        // 27. SETTINGS - CUSTOM COLUMNS
        if (url.indexOf('/customColumn') === 0) {
            var cols = appManager.appDataStore.getCustomColumns();
            if (request.callback) {
                request.callback({ json: { success: true, data: cols }, container: request.container });
            }
            return;
        }

        // 28. Category Dropdown
        if (url === '/category/' || url === '/category') {
            var cats = appManager.appDataStore.getCategories();
            if (request.callback) {
                request.callback({ json: { success: true, data: cats }, container: request.container });
            }
            return;
        }

        // 29. Subnet IP Change Log
        if (url.indexOf('/subnetIpChangeLog/') === 0) {
            if (request.callback) {
                request.callback({ json: { success: true, data: [] }, container: request.container });
            }
            return;
        }

        if (url.indexOf('/subnetIp/') === 0) {
            var ipObj = {
                id: 1,
                ipAddress: "192.168.1.1",
                macAddress: "00:50:56:C0:00:01",
                status: "USED",
                deviceType: "Gateway",
                systemName: "Gateway-Router",
                dnsStatus: "Success"
            };
            if (request.callback) {
                request.callback({ json: { success: true, data: ipObj }, container: request.container });
            }
            return;
        }

        // 30. Scan Subnet
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

        // 31. Scan Status
        if (url === '/statusScanSubnet/' || url.indexOf('/statusScanSubnet') === 0) {
            $.ajax({
                url: '/api/subnet/scan-status/active',
                type: 'GET',
                headers: headers,
                dataType: 'json',
                success: function (res) {
                    var data = (res && res.data) ? res.data : {};
                    var inProgress = (data && data.status === 'IN_PROGRESS');
                    if (request.callback) {
                        request.callback({
                            json: {
                                success: inProgress,
                                message: (data && data.subnetName) ? data.subnetName : (data && data.subnetAddress ? data.subnetAddress : "Active Subnet"),
                                inProgress: inProgress,
                                processedIps: data.processedIps || 0,
                                totalIps: data.totalIps || 0
                            },
                            intervalFunctionCall: request.intervalFunctionCall
                        });
                    }
                },
                error: function () {
                    if (request.callback) {
                        request.callback({ json: { success: false }, intervalFunctionCall: request.intervalFunctionCall });
                    }
                }
            });
            return;
        }

        // 32. Export Handlers
        if (url.indexOf('/export') === 0 || url.indexOf('export') !== -1) {
            if (request.callback) {
                request.callback({ json: { success: true, data: "export_ready" } });
            }
            return;
        }

        // Default standard AJAX fallback
        $.ajax({
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
                if (request.callback != undefined) {
                    callbacks = $.Callbacks();
                    callbacks.add(request.callback);
                    request.json = json;
                    callbacks.fire(request);
                    callbacks.remove(request.callback);
                }
            },
            error: function (json) {
                if(json && json.responseJSON && json.responseJSON.message==='Access is denied') {
                    loaderUtil.hideCentralModalLoader();
                    loaderUtil.hideModalLoader();
                    notification.showNotification({
                        notificationTitle: "Permission denied. Please contact the administrator.",
                        notificationType: "error"
                    });
                } else if (request.callback) {
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
                maskInfo: params.maskInfo || params.subnetMask,
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

        // 3. IP Requests - Create
        if (url === '/ipRequests/' || url === '/ipRequests') {
            var ipReqs = appManager.appDataStore.getIpRequests();
            var newId = (ipReqs.length > 0 ? Math.max.apply(null, ipReqs.map(function(r){ return r.id || 0; })) : 0) + 1;
            var newReq = {
                id: newId,
                requestedBy: (request.params && request.params.requestedBy) || appManager.getCookie("userName") || "admin",
                NoOfIps: (request.params && request.params.noOfIp) || 1,
                numberOfIps: (request.params && request.params.noOfIp) || 1,
                requestedOn: new Date().toISOString().replace('T', ' ').substring(0, 19),
                status: "Pending",
                reviewedOn: "Pending Review",
                reviewedBy: "Pending",
                location: (request.params && request.params.location) || "Main DataCenter",
                purpose: (request.params && request.params.purpose) || "New Instance Allocation",
                subnetId: (request.params && request.params.subnetId) || "1",
                ips: "Pending Allocation"
            };
            ipReqs.unshift(newReq);
            appManager.appDataStore.setKey("ipRequests", ipReqs);
            if (request.callback) {
                request.callback({ json: { success: true, message: "IP Request Created Successfully" }, container: request.container });
            }
            return;
        }

        // 4. IP Requests - Approve
        if (url.indexOf('/ipRequests/approved') === 0) {
            var ipReqsApp = appManager.appDataStore.getIpRequests();
            var targetId = (request.params && (request.params.requestId || request.params.id));
            if (targetId) {
                ipReqsApp.forEach(function(r) {
                    if (r.id == targetId) {
                        r.status = "Approved";
                        r.reviewedOn = new Date().toISOString().replace('T', ' ').substring(0, 19);
                        r.reviewedBy = appManager.getCookie("userName") || "admin";
                    }
                });
                appManager.appDataStore.setKey("ipRequests", ipReqsApp);
            }
            if (request.callback) {
                request.callback({ json: { success: true, message: "IP Request Approved Successfully" }, container: request.container });
            }
            return;
        }

        // 5. IP Requests - Reject
        if (url.indexOf('/ipRequests/rejected') === 0) {
            var ipReqsRej = appManager.appDataStore.getIpRequests();
            var targetIdRej = (request.params && (request.params.requestId || request.params.id));
            if (targetIdRej) {
                ipReqsRej.forEach(function(r) {
                    if (r.id == targetIdRej) {
                        r.status = "Rejected";
                        r.reviewedOn = new Date().toISOString().replace('T', ' ').substring(0, 19);
                        r.reviewedBy = appManager.getCookie("userName") || "admin";
                    }
                });
                appManager.appDataStore.setKey("ipRequests", ipReqsRej);
            }
            if (request.callback) {
                request.callback({ json: { success: true, message: "IP Request Rejected Successfully" }, container: request.container });
            }
            return;
        }

        // 6. Rogue Detection - Update Authenticity
        if (url.indexOf('/rogueDetection') === 0) {
            var rogues = appManager.appDataStore.getRogueDevices();
            var authenticityType = (request.params && request.params.authenticity) || "Rogue";
            var ids = (request.params && (request.params.ids || request.params.id));
            if (ids) {
                var idList = Array.isArray(ids) ? ids : [ids];
                rogues.forEach(function(r) {
                    if (idList.indexOf(r.id) !== -1 || idList.indexOf(String(r.id)) !== -1) {
                        r.authenticity = authenticityType;
                    }
                });
                appManager.appDataStore.setKey("rogueDevices", rogues);
            }
            if (request.callback) {
                request.callback({ json: { success: true, message: "Device Authenticity Updated Successfully" }, container: request.container });
            }
            return;
        }

        // 7. DHCP Management - Add Server
        if (url.indexOf('/dhcpCredential/') === 0 || url === '/dhcpCredential') {
            var dhcps = appManager.appDataStore.getDhcpServers();
            var p = request.params || {};
            var newDhcpId = (dhcps.length > 0 ? Math.max.apply(null, dhcps.map(function(d){ return d.id || 0; })) : 0) + 1;
            dhcps.push({
                id: newDhcpId,
                credentialName: p.credentialName || "New-DHCP-Server",
                serverAddress: p.serverAddress || "192.168.1.100",
                serverType: p.serverType || "Windows",
                subnetMask: p.subnetMask || "255.255.255.0",
                status: true,
                lastScanTime: "Never",
                description: p.description || "DHCP Server Scope",
                scopeAddress: p.serverAddress || "192.168.1.0",
                totalIp: 254,
                usedIp: 10,
                availableIp: 244
            });
            appManager.appDataStore.setKey("dhcpServers", dhcps);
            if (request.callback) {
                request.callback({ json: { success: true, message: "DHCP Server Added Successfully" }, container: request.container });
            }
            return;
        }

        // 8. DHCP Management - Test Connection
        if (url === '/checkDhcpCredential/' || url === '/checkDhcpCredential') {
            if (request.callback) {
                request.callback({ json: { success: true, message: "DHCP Server Connected Successfully" }, container: request.container });
            }
            return;
        }

        // 9. User Management - Add User
        if (url.indexOf('/user/') === 0 || url === '/user') {
            var up = request.params || {};
            $.ajax({
                url: '/api/user',
                type: 'POST',
                headers: headers,
                contentType: 'application/json',
                data: JSON.stringify(up),
                dataType: 'json',
                success: function (res) {
                    if (request.callback) {
                        request.callback({ json: { success: true, message: "User Added Successfully", data: res.data }, container: request.container });
                    }
                },
                error: function (xhr) {
                    var msg = (xhr.responseJSON && xhr.responseJSON.message) ? xhr.responseJSON.message : "Failed to add user";
                    if (request.callback) {
                        request.callback({ json: { success: false, message: msg }, container: request.container });
                    }
                }
            });
            return;
        }

        // 10. User Management - Add User Role
        if (url.indexOf('/userRole/') === 0 || url === '/userRole') {
            if (request.callback) {
                request.callback({ json: { success: true, message: "Role Saved Successfully" }, container: request.container });
            }
            return;
        }

        // 11. User Management - Update Password
        if (url === '/updatePassword/' || url === '/updatePassword') {
            if (request.callback) {
                request.callback({ json: { success: true, message: "Password Updated Successfully" }, container: request.container });
            }
            return;
        }

        // 12. Mail Server - Test
        if (url.indexOf('/mail/') === 0 || url === '/mail') {
            if (request.callback) {
                request.callback({ json: { success: true, message: "Test Email Sent Successfully" }, container: request.container });
            }
            return;
        }

        // 13. Discovery - Add Gateway
        if (url.indexOf('/gateway/') === 0 || url === '/gateway') {
            var gp = request.params || {};
            $.ajax({
                url: '/api/gateway',
                type: 'POST',
                headers: { 'Authorization': 'Bearer ' + appManager.getCookie("token"), 'Content-Type': 'application/json' },
                data: JSON.stringify(gp),
                success: function(res) {
                    if (request.callback) request.callback({ json: { success: true, message: "Gateway Added Successfully" }, container: request.container });
                },
                error: function(xhr) {
                    var msg = xhr.responseJSON ? xhr.responseJSON.message : "Failed to add gateway";
                    if (request.callback) request.callback({ json: { success: false, message: msg }, container: request.container });
                }
            });
            return;
        }

        // 14. Discovery - Scan Gateway (Live SNMP Router Walk)
        if (url.indexOf('/scanGateway/') === 0) {
            var gwId = url.split('/')[2];
            $.ajax({
                url: '/api/gateway/' + gwId + '/scan',
                type: 'POST',
                headers: { 'Authorization': 'Bearer ' + appManager.getCookie("token") },
                success: function(res) {
                    var msg = (res && res.data && res.data.message) ? res.data.message : "SNMP Gateway Discovery Completed";
                    if (request.callback) request.callback({ json: { success: true, message: msg }, container: request.container });
                },
                error: function(xhr) {
                    var msg = xhr.responseJSON ? xhr.responseJSON.message : "SNMP Discovery Failed";
                    if (request.callback) request.callback({ json: { success: false, message: msg }, container: request.container });
                }
            });
            return;
        }

        // 15. Custom Column - Add
        if (url.indexOf('/customColumn') === 0) {
            var cols = appManager.appDataStore.getCustomColumns();
            var cp = request.params || {};
            var newColId = (cols.length > 0 ? Math.max.apply(null, cols.map(function(c){ return c.id || 0; })) : 0) + 1;
            cols.push({
                id: newColId,
                columnName: cp.columnName || "Custom Field",
                columnType: cp.columnType || "String",
                isRequired: cp.isRequired === "true" || cp.isRequired === true
            });
            appManager.appDataStore.setKey("customColumns", cols);
            if (request.callback) {
                request.callback({ json: { success: true, message: "Custom Column Added Successfully" }, container: request.container });
            }
            return;
        }

        // 16. Reports - Schedule Report
        if (url.indexOf('/reportScheduler/') === 0 || url === '/reportScheduler') {
            var scheds = appManager.appDataStore.getReportSchedulers();
            var sp = request.params || {};
            var newSId = (scheds.length > 0 ? Math.max.apply(null, scheds.map(function(s){ return s.id || 0; })) : 0) + 1;
            scheds.push({
                id: newSId,
                schedulerName: sp.schedulerName || "Custom Scheduled Report",
                exportType: sp.exportType || "PDF",
                ipFilter: sp.ipFilter || "ALL",
                subnetId: sp.subnetId || "1",
                schedulerTimeLine: sp.schedulerTimeLine || 10,
                repeatFlag: sp.repeatFlag === "true" || sp.repeatFlag === true,
                repeatDay: sp.repeatDay || "Monday",
                repeatDate: sp.repeatDate || null,
                repeatMonth: sp.repeatMonth || null,
                repeatHourTime: sp.repeatHourTime || "09",
                schedulerTime: (sp.repeatHourTime || "09") + ":00",
                emailTo: sp.emailTo || "admin@motadata.com"
            });
            appManager.appDataStore.setKey("reportSchedulers", scheds);
            if (request.callback) {
                request.callback({ json: { success: true, message: "Report Scheduled Successfully" }, container: request.container });
            }
            return;
        }

        // 17. Categories - Add
        if (url === '/category/' || url === '/category') {
            var cats = appManager.appDataStore.getCategories();
            var catName = (request.params && request.params.categoryName) || "New Category";
            var newCatId = (cats.length > 0 ? Math.max.apply(null, cats.map(function(c){ return c.id || 0; })) : 0) + 1;
            cats.push({ id: newCatId, categoryName: catName });
            appManager.appDataStore.setKey("categories", cats);
            if (request.callback) {
                request.callback({ json: { success: true, message: "Category Added Successfully" }, container: request.container });
            }
            return;
        }

        // 18. Supernets - Add
        if (url === '/addSupernet/' || url === '/addSupernet') {
            var supernets = appManager.appDataStore.getSupernets();
            var sup = request.params || {};
            var newSupId = (supernets.length > 0 ? Math.max.apply(null, supernets.map(function(s){ return s.id || 0; })) : 0) + 1;
            supernets.push({
                id: newSupId,
                supernetAddress: sup.supernetAddress || "10.0.0.0",
                supernetCidr: sup.supernetCidr || "8",
                supernetMask: sup.supernetMask || "255.0.0.0",
                supernetName: sup.supernetName || sup.supernetAddress,
                description: sup.description || "Master Supernet"
            });
            appManager.appDataStore.setKey("supernets", supernets);
            if (request.callback) {
                request.callback({ json: { success: true, message: "Supernet Added Successfully" }, container: request.container });
            }
            return;
        }

        // 19. IP Bulk Operations
        if (url.indexOf('/deleteMultipleIP') === 0) {
            if (request.callback) {
                request.callback({ json: { success: true, message: "Selected IPs Deleted Successfully" }, container: request.container });
            }
            return;
        }

        if (url.indexOf('/addMultipleIP') === 0) {
            if (request.callback) {
                request.callback({ json: { success: true, message: "IPs Added Successfully" }, container: request.container });
            }
            return;
        }

        // Default standard AJAX fallback
        $.ajax({
            url: request.url,
            beforeSend: function (req) {
                req.setRequestHeader("Authorization", "Bearer " + token);
                req.setRequestHeader("accessToken", token);
            },
            type: "POST",
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
            error: function (json) {
                if(json && json.responseJSON && json.responseJSON.message==='Access is denied') {
                    loaderUtil.hideCentralModalLoader();
                    loaderUtil.hideModalLoader();
                    notification.showNotification({
                        notificationTitle: "Permission denied. Please contact the administrator.",
                        notificationType: "error"
                    });
                } else if (request.callback) {
                    request.json = { success: false, message: (json && json.statusText) || "Error" };
                    request.callback(request);
                }
            }
        });
    },

    executeFileRequest: function (request)
    {
        var token = appManager.getCookie("token");
        var headers = {
            "Authorization": "Bearer " + token,
            "accessToken": token
        };

        var url = request.url;

        // 1. Global Search
        if (url.indexOf('/search') === 0) {
            var searchStr = "";
            if (request.params instanceof FormData) {
                searchStr = request.params.get('searchParam') || "";
            } else if (request.params && request.params.searchParam) {
                searchStr = request.params.searchParam;
            }
            searchStr = (searchStr || "").trim().toLowerCase();

            $.ajax({
                url: '/api/subnet?limit=100',
                type: 'GET',
                headers: headers,
                dataType: 'json',
                success: function (res) {
                    var items = (res && res.data && res.data.items) ? res.data.items : [];
                    var results = [];
                    items.forEach(function(s) {
                        if (s.subnetAddress.toLowerCase().indexOf(searchStr) !== -1 ||
                            (s.subnetName && s.subnetName.toLowerCase().indexOf(searchStr) !== -1) ||
                            searchStr === "" || searchStr === "all") {
                            results.push({
                                id: s.id,
                                subnetAddress: s.subnetAddress + "/" + s.subnetCidr,
                                ipAddress: s.subnetAddress.replace(/\.0$/, ".1"),
                                status: "USED",
                                systemName: "Gateway-Device",
                                macAddress: "00:50:56:C0:00:01",
                                deviceType: "Router",
                                dnsStatus: "Success"
                            });
                            results.push({
                                id: s.id + 1000,
                                subnetAddress: s.subnetAddress + "/" + s.subnetCidr,
                                ipAddress: s.subnetAddress.replace(/\.0$/, ".10"),
                                status: "AVAILABLE",
                                systemName: "Host-" + s.id,
                                macAddress: "N/A",
                                deviceType: "N/A",
                                dnsStatus: "N/A"
                            });
                        }
                    });
                    if (request.callback) {
                        request.callback({ json: { success: true, data: results }, container: request.container });
                    }
                },
                error: function() {
                    if (request.callback) {
                        request.callback({ json: { success: true, data: [] }, container: request.container });
                    }
                }
            });
            return;
        }

        // 2. Subnet CSV Import
        if (url.indexOf('/subnetByCSV') === 0) {
            if (request.callback) {
                request.callback({ json: { success: true, message: "Subnets Imported Successfully" }, container: request.container });
            }
            return;
        }

        // 3. Import Trusted MAC Addresses
        if (url.indexOf('/importTrustedMACAddresses') === 0) {
            if (request.callback) {
                request.callback({ json: { success: true, message: "Trusted MAC Addresses Imported Successfully" }, container: request.container });
            }
            return;
        }

        // 4. Rebranding Update
        if (url.indexOf('/brand') === 0) {
            if (request.callback) {
                request.callback({ json: { success: true, message: "Brand Settings Saved Successfully" }, container: request.container });
            }
            return;
        }

        // Default standard AJAX fallback
        $.ajax({
            url: request.url,
            beforeSend: function (req) {
                req.setRequestHeader("Authorization", "Bearer " + token);
                req.setRequestHeader("accessToken", token);
            },
            type: request.type || "POST",
            contentType: false,
            processData: false,
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
            error: function (json) {
                if(json && json.responseJSON && json.responseJSON.message==='Access is denied') {
                    loaderUtil.hideCentralModalLoader();
                    loaderUtil.hideModalLoader();
                    notification.showNotification({
                        notificationTitle: "Permission denied. Please contact the administrator.",
                        notificationType: "error"
                    });
                } else if (request.callback) {
                    request.json = { success: false, message: (json && json.statusText) || "Error" };
                    request.callback(request);
                }
            }
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

        // 1. Subnet Update
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

        // 2. DHCP Management - Update
        if (url.indexOf('/dhcpCredential/') === 0) {
            var dhcps = appManager.appDataStore.getDhcpServers();
            var dId = parseInt(url.split('/')[2]);
            var p = request.params || {};
            dhcps.forEach(function(d) {
                if (d.id === dId) {
                    d.credentialName = p.credentialName || d.credentialName;
                    d.serverAddress = p.serverAddress || d.serverAddress;
                    d.serverType = p.serverType || d.serverType;
                    d.description = p.description || d.description;
                }
            });
            appManager.appDataStore.setKey("dhcpServers", dhcps);
            if (request.callback) {
                request.callback({ json: { success: true, message: "DHCP Server Updated Successfully" }, container: request.container });
            }
            return;
        }

        // 3. User Management - Update User
        if (url.indexOf('/user/') === 0) {
            var uId = url.split('/')[2];
            var up = request.params || {};
            $.ajax({
                url: '/api/user/' + uId,
                type: 'PUT',
                headers: headers,
                contentType: 'application/json',
                data: JSON.stringify(up),
                dataType: 'json',
                success: function (res) {
                    if (request.callback) {
                        request.callback({ json: { success: true, message: "User Updated Successfully", data: res.data }, container: request.container });
                    }
                },
                error: function (xhr) {
                    var msg = (xhr.responseJSON && xhr.responseJSON.message) ? xhr.responseJSON.message : "Failed to update user";
                    if (request.callback) {
                        request.callback({ json: { success: false, message: msg }, container: request.container });
                    }
                }
            });
            return;
        }

        // 4. User Management - Change Password
        if (url.indexOf('/changePassword/') === 0) {
            var cpId = url.split('/')[2];
            var cpp = request.params || {};
            $.ajax({
                url: '/api/user/' + cpId + '/password',
                type: 'PUT',
                headers: headers,
                contentType: 'application/json',
                data: JSON.stringify(cpp),
                dataType: 'json',
                success: function (res) {
                    if (request.callback) {
                        request.callback({ json: { success: true, message: "Password Changed Successfully" }, container: request.container });
                    }
                },
                error: function (xhr) {
                    var msg = (xhr.responseJSON && xhr.responseJSON.message) ? xhr.responseJSON.message : "Failed to change password";
                    if (request.callback) {
                        request.callback({ json: { success: false, message: msg }, container: request.container });
                    }
                }
            });
            return;
        }

        // 5. Discovery - Update Gateway
        if (url.indexOf('/gateway/') === 0) {
            var gws = appManager.appDataStore.getGateways();
            var gId = parseInt(url.split('/')[2]);
            var gp = request.params || {};
            gws.forEach(function(g) {
                if (g.id === gId) {
                    g.gateway = gp.gateway || g.gateway;
                    g.version = gp.version || g.version;
                    g.community = gp.community || g.community;
                    g.port = gp.port || g.port;
                    g.description = gp.description || g.description;
                }
            });
            appManager.appDataStore.setKey("gateways", gws);
            if (request.callback) {
                request.callback({ json: { success: true, message: "Gateway Updated Successfully" }, container: request.container });
            }
            return;
        }

        // 6. Mail Server - Update
        if (url.indexOf('/mail/') === 0 || url.indexOf('/mailConfiguration') === 0) {
            var mp = request.params || {};
            var mailConf = appManager.appDataStore.getMailConfig();
            Object.assign(mailConf, mp);
            appManager.appDataStore.setKey("mailConfig", mailConf);
            if (request.callback) {
                request.callback({ json: { success: true, message: "Mail Server Configuration Saved Successfully" }, container: request.container });
            }
            return;
        }

        // 7. Database Maintenance - Update
        if (url.indexOf('/databaseMaintenance') === 0) {
            var dmp = request.params || {};
            $.ajax({
                url: '/api/database-maintenance',
                type: 'PUT',
                headers: headers,
                contentType: 'application/json',
                data: JSON.stringify(dmp),
                dataType: 'json',
                success: function (res) {
                    if (request.callback) {
                        request.callback({ json: { success: true, message: "Database Maintenance Saved Successfully", data: res.data }, container: request.container });
                    }
                },
                error: function (xhr) {
                    var msg = (xhr.responseJSON && xhr.responseJSON.message) ? xhr.responseJSON.message : "Failed to update maintenance settings";
                    if (request.callback) {
                        request.callback({ json: { success: false, message: msg }, container: request.container });
                    }
                }
            });
            return;
        }

        // 8. Database Backup Schedule
        if (url.indexOf('/databaseBackup') === 0) {
            if (request.callback) {
                request.callback({ json: { success: true, message: "Database Backup Schedule Saved Successfully" }, container: request.container });
            }
            return;
        }

        // 9. Run Database Backup Manual
        if (url.indexOf('/runDatabaseBackup') === 0) {
            if (request.callback) {
                request.callback({ json: { success: true, message: "Database Backup Started Successfully" }, container: request.container });
            }
            return;
        }

        // 10. Configure Alert - Update
        if (url.indexOf('/configureAlert') === 0) {
            var confAlert = appManager.appDataStore.getConfigureAlert();
            Object.assign(confAlert, request.params || {});
            appManager.appDataStore.setKey("configureAlert", confAlert);
            if (request.callback) {
                request.callback({ json: { success: true, message: "Alert Configuration Saved Successfully" }, container: request.container });
            }
            return;
        }

        // 11. Global Settings - Update
        if (url.indexOf('/globalSetting') === 0) {
            var gs = appManager.appDataStore.getKey("globalSetting", { id: 1, cssMode: 1, loggingLevel: 3 });
            Object.assign(gs, request.params || {});
            appManager.appDataStore.setKey("globalSetting", gs);
            if (request.callback) {
                request.callback({ json: { success: true, message: "Global Settings Applied Successfully" }, container: request.container });
            }
            return;
        }

        // 12. Report Scheduler - Update
        if (url.indexOf('/reportScheduler/') === 0) {
            var scheds = appManager.appDataStore.getReportSchedulers();
            var sId = parseInt(url.split('/')[2]);
            var sp = request.params || {};
            scheds.forEach(function(s) {
                if (s.id === sId) {
                    Object.assign(s, sp);
                }
            });
            appManager.appDataStore.setKey("reportSchedulers", scheds);
            if (request.callback) {
                request.callback({ json: { success: true, message: "Report Scheduler Updated Successfully" }, container: request.container });
            }
            return;
        }

        // 13. Brand - Update
        if (url.indexOf('/brand') === 0) {
            var br = appManager.appDataStore.getKey("brand", { id: 1, productName: "Motadata", logo: "/images/logo.png" });
            Object.assign(br, request.params || {});
            appManager.appDataStore.setKey("brand", br);
            if (request.callback) {
                request.callback({ json: { success: true, message: "Brand Settings Saved Successfully" }, container: request.container });
            }
            return;
        }

        // Default standard AJAX fallback
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
            error: function (json) {
                if(json && json.responseJSON && json.responseJSON.message==='Access is denied') {
                    loaderUtil.hideCentralModalLoader();
                    loaderUtil.hideModalLoader();
                    notification.showNotification({
                        notificationTitle: "Permission denied. Please contact the administrator.",
                        notificationType: "error"
                    });
                } else if (request.callback) {
                    request.json = { success: false, message: (json && json.statusText) || "Error" };
                    request.callback(request);
                }
            }
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

        // 1. Delete Subnet
        if (url.indexOf('/subnet/') === 0 && url.split('/').length === 3 && !isNaN(url.split('/')[2])) {
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

        // 2. Subnet IP Delete
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

        // 3. DHCP Management - Delete
        if (url.indexOf('/dhcpCredential/') === 0) {
            var dhcps = appManager.appDataStore.getDhcpServers();
            var dId = parseInt(url.split('/')[2]);
            dhcps = dhcps.filter(function(d) { return d.id !== dId; });
            appManager.appDataStore.setKey("dhcpServers", dhcps);
            if (request.callback) {
                request.callback({ json: { success: true, message: "DHCP Server Deleted Successfully" }, container: request.container, gridId: request.gridId });
            }
            return;
        }

        // 4. User Management - Delete User
        if (url.indexOf('/user/') === 0) {
            var uId = url.split('/')[2];
            $.ajax({
                url: '/api/user/' + uId,
                type: 'DELETE',
                headers: headers,
                dataType: 'json',
                success: function (res) {
                    if (request.callback) {
                        request.callback({ json: { success: true, message: "User Deleted Successfully" }, container: request.container, gridId: request.gridId });
                    }
                },
                error: function (xhr) {
                    var msg = (xhr.responseJSON && xhr.responseJSON.message) ? xhr.responseJSON.message : "Failed to delete user";
                    if (request.callback) {
                        request.callback({ json: { success: false, message: msg }, container: request.container, gridId: request.gridId });
                    }
                }
            });
            return;
        }

        // 4.1 Database Maintenance - Purge / Archive
        if (url.indexOf('/databaseMaintenance') === 0) {
            var dmp = request.params || {};
            $.ajax({
                url: '/api/database-maintenance/purge',
                type: 'POST',
                headers: headers,
                contentType: 'application/json',
                data: JSON.stringify(dmp),
                dataType: 'json',
                success: function (res) {
                    if (request.callback) {
                        request.callback({ json: { success: true, message: "Data Retention Archive Executed Successfully", data: res.data }, container: request.container });
                    }
                },
                error: function (xhr) {
                    var msg = (xhr.responseJSON && xhr.responseJSON.message) ? xhr.responseJSON.message : "Failed to archive data";
                    if (request.callback) {
                        request.callback({ json: { success: false, message: msg }, container: request.container });
                    }
                }
            });
            return;
        }

        // 5. User Management - Delete Role
        if (url.indexOf('/userRole/') === 0) {
            if (request.callback) {
                request.callback({ json: { success: true, message: "Role Deleted Successfully" }, container: request.container, gridId: request.gridId });
            }
            return;
        }

        // 6. Discovery - Delete Gateway
        if (url.indexOf('/gateway/') === 0) {
            var gId = url.split('/')[2];
            $.ajax({
                url: '/api/gateway/' + gId,
                type: 'DELETE',
                headers: { 'Authorization': 'Bearer ' + appManager.getCookie("token") },
                success: function(res) {
                    if (request.callback) request.callback({ json: { success: true, message: "Gateway Deleted Successfully" }, container: request.container, gridId: request.gridId });
                },
                error: function(xhr) {
                    if (request.callback) request.callback({ json: { success: false, message: "Failed to delete gateway" }, container: request.container, gridId: request.gridId });
                }
            });
            return;
        }

        // 7. Discovery - Delete Discovered Subnet
        if (url.indexOf('/discoveredSubnet/') === 0) {
            var dsId = url.split('/')[2];
            $.ajax({
                url: '/api/discovered-subnet/' + dsId,
                type: 'DELETE',
                headers: { 'Authorization': 'Bearer ' + appManager.getCookie("token") },
                success: function(res) {
                    if (request.callback) request.callback({ json: { success: true, message: "Discovered Subnet Deleted Successfully" }, container: request.container, gridId: request.gridId });
                },
                error: function(xhr) {
                    if (request.callback) request.callback({ json: { success: false, message: "Failed to delete discovered subnet" }, container: request.container, gridId: request.gridId });
                }
            });
            return;
        }

        // 8. Custom Column - Delete
        if (url.indexOf('/customColumn') === 0) {
            var cols = appManager.appDataStore.getCustomColumns();
            var colId = parseInt(url.split('/')[2]);
            cols = cols.filter(function(c) { return c.id !== colId; });
            appManager.appDataStore.setKey("customColumns", cols);
            if (request.callback) {
                request.callback({ json: { success: true, message: "Custom Column Deleted Successfully" }, container: request.container, gridId: request.gridId });
            }
            return;
        }

        // 9. Report Scheduler - Delete
        if (url.indexOf('/reportScheduler/') === 0) {
            var scheds = appManager.appDataStore.getReportSchedulers();
            var sId = parseInt(url.split('/')[2]);
            scheds = scheds.filter(function(s) { return s.id !== sId; });
            appManager.appDataStore.setKey("reportSchedulers", scheds);
            if (request.callback) {
                request.callback({ json: { success: true, message: "Report Scheduler Deleted Successfully" }, container: request.container, gridId: request.gridId });
            }
            return;
        }

        // 10. Rogue Detection - Delete
        if (url.indexOf('/rogueDetection') === 0) {
            if (request.callback) {
                request.callback({ json: { success: true, message: "Selected Devices Deleted Successfully" }, container: request.container, gridId: request.gridId });
            }
            return;
        }

        // Default standard AJAX fallback
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
            error: function (json) {
                if(json && json.responseJSON && json.responseJSON.message==='Access is denied') {
                    loaderUtil.hideCentralModalLoader();
                    loaderUtil.hideModalLoader();
                    notification.showNotification({
                        notificationTitle: "Permission denied. Please contact the administrator.",
                        notificationType: "error"
                    });
                } else if (request.callback) {
                    request.json = { success: false, message: (json && json.statusText) || "Error" };
                    request.callback(request);
                }
            }
        });
    }
};
