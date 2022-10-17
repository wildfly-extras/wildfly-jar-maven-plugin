/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2020 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

$(document).ready(function () {

    $("#stopAll").click(function () {
        $.ajax("rest/log/stop", {
            method: "POST",
            error: errorHandler,
            success: function (data) {
                if (data.length === 0) {
                    createNotification("No Active Jobs", "No active jobs to stop.")
                }
                $.each(data, function (i, item) {
                    if (item.cancelled === true) {
                        createNotification("Job Stopped", "Successfully stopped job with id" + item.id);
                    } else {
                        createNotification("Job Not Stopped", "Job " + item.id + " was not successfully stopped.");
                    }
                });
            }
        });
    });
    $("#start").click(function () {
        $.ajax("rest/log/start/" + seconds, {
            method: "POST",
            error: errorHandler,
            success: function (data) {
                refreshLogs();
                createNotification("Job Started", "Scheduled job with id " + data.id + " to run every " + seconds + " seconds.");
            }
        });
    });

    $("#logError").click(function () {
        $.ajax("rest/log/error", {
            error: errorHandler,
            success: function () {
                refreshLogs();
            }
        });
    });

    $("#refresh").click(function () {
        refreshLogs();
    });

    $("#activeJobs").click(function () {
        $("#jobs").bootstrapTable("refresh");
    });
    $("#jobModal").on("hidden.bs.modal", function () {
        $("#jobs").bootstrapTable("removeAll");
    });

    let logModal = $("#logMessageModal");
    let logForm = $("#logMessageForm");
    $("#logMessage").click(function () {
        logModal.modal("show");
        $("#message").focus();
    });
    logForm.submit(function (e) {
        e.preventDefault();
        let data = {};
        $.each(logForm.serializeArray(), function (i, field) {
            data[field.name] = field.value || "";
        });
        $.ajax("rest/log/", {
            type: "POST",
            data: JSON.stringify(data),
            complete: function () {
                logModal.modal("hide");
            },
            error: errorHandler,
            success: function () {
                refreshLogs();
            },
            dataType: "json",
            contentType: "application/json"
        });
    });

    $("#jsonLogs").click(function () {
        $.ajax("rest/log/", {
            error: function (xhr, textStatus, errorThrown) {
                let body = xhr.status + " " + textStatus + " " + errorThrown;
                $("#rawJson").text(body);
            },
            complete: function () {
                $("#jsonModal").modal("show");
            },
            success: function (data) {
                $("#rawJson").text(JSON.stringify(data, null, '\t'));
            }
        });
    });

    let autoRefresh = false;
    if (localStorage.autoRefresh) {
        autoRefresh = (localStorage.autoRefresh === "true");
    } else {
        localStorage.autoRefresh = autoRefresh;
    }
    let intvId;
    if (autoRefresh) {
        intvId = scheduleAutoRefresh();
    }
    $("#autoRefresh").click(function () {
        if (this.checked) {
            intvId = scheduleAutoRefresh();
            localStorage.autoRefresh = autoRefresh = true;
        } else {
            if (intvId) {
                clearInterval(intvId);
            }
            localStorage.autoRefresh = autoRefresh = false;
        }
    }).prop("checked", autoRefresh);

    let seconds = 5;
    if (localStorage.seconds) {
        seconds = localStorage.seconds;
    } else {
        localStorage.seconds = seconds;
    }
    $("#seconds").val(seconds)
        .change(function () {
            seconds = $(this).val();
            localStorage.seconds = seconds;
        });

    let detailRows = new Set();
    let offset = 0;
    $("#logs").on("expand-row.bs.table", function (event, row) {
        detailRows.add(row);
    }).on("collapse-row.bs.table", function (event, row) {
        detailRows.delete(row);
    }).on("post-body.bs.table", function () {
        let $table = $("#logs");
        let rows = $table.bootstrapTable('getOptions').totalRows;
        offset = rows - offset;
        const a = Array.from(detailRows);
        for (const i of a) {
            let pos = i;
            if (offset > 0) {
                detailRows.delete(i);
                pos = offset + pos;
                detailRows.add(pos);
            }
            $table.bootstrapTable("toggleDetailView", pos);
        }
        offset = rows;
    }).on("refresh.bs.table", function () {
        offset = $("#logs").bootstrapTable('getOptions').totalRows;
    });
});

window.operateEvent = {
    'click .remove': function (e, value, row) {
        $.ajax("rest/log/stop/" + row.id, {
            method: "POST",
            success: function () {
                $("#jobs").bootstrapTable("remove", {
                    field: 'id',
                    values: [row.id]
                });
            }
        });
    }
};

function refreshLogs() {
    let $table = $("#logs");
    let pos = $table.bootstrapTable("getScrollPosition");
    $table.bootstrapTable("refresh");
    $table.on("post-body.bs.table", function () {
        $table.bootstrapTable("scrollTo", {unit: "px", value: pos});
    });
}

function scheduleAutoRefresh() {
    refreshLogs();
    return setInterval(function () {
        refreshLogs()
    }, 5000);
}

// noinspection JSUnusedGlobalSymbols
function buttonFormatter() {
    return [
        '<a class="remove" href="javascript:void(0)" title="Remove">',
        '<button class="btn btn-small btn-danger">Stop</button>',
        '</a>'
    ].join("");
}

// noinspection JSUnusedGlobalSymbols
function detailFormatter(index, row) {
    // noinspection JSUnresolvedVariable
    return [
        '<pre class="text-monospace">',
        '<code>',
        '<span class="mx-1">' + row.timestamp + '</span>',
        '<span class="mx-1">' + row.level.padEnd(5, " ") + '</span>',
        '<span class="mx-1">[' + row.loggerName + ']</span>',
        '<spam class="mx-1">(' + row.threadName + ')</spam>',
        '<span class="mx-1">' + row.message + '</span>',
        '<span class="mx-1">' + row.stackTrace ? row.stackTrace : "" + '</span>',
        '</code>',
        '</pre>'
    ].join('');
}

// noinspection JSUnusedGlobalSymbols
function dateFormatter(value) {
    // return only the time for the display
    return value.slice(11, 23);
}

// noinspection JSUnusedGlobalSymbols
function logRowFormatter(row) {
    let level = row.level;
    if (level === "WARN" || level === "WARNING") {
        return {classes: "table-warning"};
    } else if (level === "ERROR" || level === "SEVERE" || level === "FATAL") {
        return {classes: "table-danger"};
    }
    return "";
}

// noinspection JSUnusedGlobalSymbols
function messageFormatter(value, row) {
    if (row.stackTrace) {
        const maxLen = 80;
        const len = row.message.length;
        let msg = row.message;
        if (len < maxLen) {
            msg = row.message + " " + row.stackTrace.substring(0, maxLen - len);
        }
        return msg;
    }
    return row.message;
}

function createNotification(title, bodyText, timeout) {
    let t = timeout ? timeout : "5000";
    let html = '<div class="toast" role="alert" aria-live="assertive" aria-atomic="true" data-delay="' + t + '">' +
        '<div class="toast-header">' +
        '<strong class="mr-auto text-primary">' + title + '</strong>' +
        '<button type="button" class="ml-2 mb-1 close" data-dismiss="toast" aria-label="Close">' +
        '<span aria-hidden="true">&times;</span>' +
        '</button>' +
        '</div>' +
        '<div id="startToastText" class="toast-body">' +
        bodyText +
        '</div>' +
        '</div>';
    let notifications = $("#notifications");
    let comp = $(html);
    $(comp).on("hidden.bs.toast", function () {
        $(comp).remove();
    });
    $(notifications).append($(comp));
    $(comp).toast("show");
}

function errorHandler(xhr, textStatus, errorThrown) {
    let body = '<strong>' + textStatus + ':</strong> ' + errorThrown;
    createNotification(xhr.status, body);
}

