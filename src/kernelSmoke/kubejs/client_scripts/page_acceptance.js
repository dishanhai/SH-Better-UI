// Author: dishanhai
const PAGE = "kubejs:page_acceptance";
const ACTION = "kubejs:page_acceptance_click";
SHUI.registerPage(PAGE, {
    title: "KubeJS page acceptance",
    textReadability: {adaptiveColor: true, foregroundProtection: true, contrast: 4.5},
    fields: { count: {type: "integer", min: 0, max: 2} },
    actions: [ACTION],
    root: {
        id: "root", type: "column", children: [
            {id: "counter", type: "text", bind: "count"},
            {id: "click", type: "button", label: "KubeJS acceptance action", action: ACTION,
                textReadability: {lockHue: true, sampling: "focused"}}
        ]
    }
});
console.info("SHB_PAGE_KUBEJS_REGISTERED");

let remaining = 10;
let openReadability = false;
ClientEvents.tick(function () {
    if (Client.player != null && remaining > 0 && --remaining === 0) {
        SHUI.open(PAGE, {count: 0});
    }
    if (openReadability) {
        openReadability = false;
        runReadability();
    }
});

SHUI.on("actionResult", function (event) {
    if (event.pageId() === PAGE && event.actionId() === ACTION) {
        console.info("SHB_PAGE_KUBEJS_ACTION");
        SHUI.update(event.instanceId(), {count: 1}).whenComplete(function (updated, error) {
            if (error != null || updated !== true) console.error("SHB_PAGE_KUBEJS_UPDATE_FAILED " + error);
        });
    }
});

SHUI.on("dataChanged", function (event) {
    if (event.pageId() === PAGE) console.info("SHB_PAGE_KUBEJS_REVISION " + event.revision());
});

SHUI.on("pageClosed", function (event) {
    if (event.pageId() === PAGE && event.revision() === 1) openReadability = true;
});

function runReadability() {
    const api = SHUI.textReadability();
    const control = "kubejs:readability_control";
    const popup = "kubejs:readability_popup";
    const ink = SHUI.text("sh_better_ui.pages.list");
    let page, frame, context, foreground, popupContext, status;
    let popupDrawn = false;
    let closing = false;
    let passed = false;
    function expect(actual, expected) {
        if (String(actual) !== expected) throw new Error("SHB_TEXT_KUBEJS expected " + expected + ", got " + actual);
    }
    api.screen("KubeJS readability acceptance").onInit(function (screen) {
        let invalid = false;
        try { api.page(screen, {contrast: 0}); } catch (error) { invalid = true; }
        if (!invalid) throw new Error("SHB_TEXT_KUBEJS accepted an invalid contrast");
        page = api.page(screen, {adaptiveColor: true, foregroundProtection: true, contrast: 4.5});
        page.control(control, {lockHue: true, sampling: "important"});
        status = page.button(20, 40, 155, 20, "KubeJS readability popup", function () {
            const opened = page.openPopup(popup, status, 150, 60,
                {inheritPage: true, contrast: 7, sampling: "declared_only"}, function (draw) {
                if (popupContext != null || passed) return;
                popupContext = draw;
                const box = draw.bounds();
                expect(draw.submit(control, "kubejs:clipped", ink, box.right() + 1, box.y() + 10, -1), "CLIPPED");
                expect(draw.submit(control, "kubejs:popup_text", ink, box.x() + 24, box.y() + 20, -1), "SUBMITTED");
                expect(draw.foreground(2, function (now) {
                    expect(now.drawNow(control, "kubejs:popup_foreground", ink, box.x() + 24, box.y() + 33, -1), "DRAWN");
                    popupDrawn = true;
                }), "SUBMITTED");
            });
            if (!opened) throw new Error("SHB_TEXT_KUBEJS could not open its owned popup");
        });
    }).onRender(function (screen, current) {
        if (frame != null) return;
        frame = current;
        const unowned = screen.context().frame(current.graphics(), current.viewport(), current.partialTick());
        expect(page.render(unowned, function () {}), "INVALID_STAGE");
        expect(page.render(current, function (draw) {
            context = draw;
            expect(draw.drawNow(control, "kubejs:wrong_stage", ink, 24, 75, -1), "INVALID_STAGE");
            expect(draw.submit("other:control", "kubejs:invalid", ink, 24, 75, -1), "INVALID_REQUEST");
            expect(draw.submit(control, "kubejs:bad_color", ink, 24, 75, 2.5), "INVALID_REQUEST");
            expect(draw.submit(control, "kubejs:page_text", ink, 24, 75, -1), "SUBMITTED");
            expect(draw.foreground(2, function (now) {
                foreground = now;
                expect(now.submit(control, "kubejs:late_submission", ink, 24, 87, -1), "INVALID_STAGE");
                expect(now.drawNow(control, "kubejs:page_foreground", ink, 24, 87, -1), "DRAWN");
            }), "SUBMITTED");
        }), "DRAWN");
    }).onTick(function () {
        if (!popupDrawn || foreground == null || passed) return;
        if (!closing) {
            expect(page.render(frame, function () {}), "INVALID_STAGE");
            expect(context.submit(control, "kubejs:expired", ink, 24, 75, -1), "INVALID_STAGE");
            expect(foreground.drawNow(control, "kubejs:expired", ink, 24, 75, -1), "INVALID_STAGE");
            if (!page.closePopup(popup)) throw new Error("SHB_TEXT_KUBEJS could not close its owned popup");
            closing = true;
        } else {
            var closedPopupResult = String(popupContext.submit(control, "kubejs:closed_popup", ink, 24, 75, -1));
            if (closedPopupResult === "INVALID_STAGE") return;
            expect(closedPopupResult, "SCOPE_CLOSED");
            passed = true;
            status.setMessage(Text.of("KubeJS readability verified"));
            console.info("SHB_TEXT_KUBEJS_VERIFIED");
        }
    }).open();
}
