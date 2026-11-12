// Author: dishanhai
const SHUI_EXAMPLE_PAGE = "kubejs:unified_page_example";
const SHUI_EXAMPLE_ACTION = "kubejs:increment_example";

SHUI.registerPage(SHUI_EXAMPLE_PAGE, {
    type: "screen",
    title: "统一页面演示",
    fields: {
        "demo.count": {type: "integer", min: 0, max: 1000}
    },
    actions: [SHUI_EXAMPLE_ACTION],
    root: {
        id: "root",
        type: "column",
        children: [
            {id: "caption", type: "text", text: "本地点击次数"},
            {id: "count", type: "text", bind: "demo.count"},
            {id: "increment", type: "button", label: "增加次数", action: SHUI_EXAMPLE_ACTION}
        ]
    }
});

let shuiExampleCountdown = 0;
let shuiExampleCount = 0;

ClientEvents.loggedIn(function () {
    shuiExampleCount = 0;
    shuiExampleCountdown = 20;
});

ClientEvents.loggedOut(function () {
    shuiExampleCountdown = 0;
});

ClientEvents.tick(function () {
    if (shuiExampleCountdown > 0 && --shuiExampleCountdown === 0) {
        SHUI.open(SHUI_EXAMPLE_PAGE, {"demo.count": 0});
    }
});

SHUI.on("actionResult", function (event) {
    if (event.pageId() === SHUI_EXAMPLE_PAGE && event.actionId() === SHUI_EXAMPLE_ACTION) {
        shuiExampleCount = Math.min(1000, shuiExampleCount + 1);
        SHUI.update(event.instanceId(), {"demo.count": shuiExampleCount});
    }
});

SHUI.on("dataChanged", function (event) {
    if (event.pageId() === SHUI_EXAMPLE_PAGE) {
        console.info("统一页面修订号 " + event.revision());
    }
});
