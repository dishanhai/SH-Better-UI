// Author: dishanhai
package com.dishanhai.uuk.worker;

import com.dishanhai.sh_better_ui.automation.script.ScriptWorkerSupervisor;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ScriptWorkerProcessTest {
    private static Path jar() { return Path.of(System.getProperty("uuk.worker.jar")); }
    private static ScriptWorkerSupervisor.Limits limits(long millis, int output, int requests) {
        return new ScriptWorkerSupervisor.Limits(65536, 32768, output, 8192, requests, 1, 1, 64, Duration.ofMillis(millis));
    }
    private static ScriptWorkerSupervisor.Result run(String script) throws Exception {
        try (var worker = new ScriptWorkerSupervisor(jar(), limits(5000, 65536, 16))) {
            return worker.submit(script, "{\"value\":21}").get(7, TimeUnit.SECONDS);
        }
    }

    @Test void returnsPureDataAndCapabilityIntents() throws Exception {
        var result = run("uuk.request('uuk.page.open',{page:'demo'}); ({answer:input.value*2, names:['a','b']})");
        assertTrue(result.success(), result.errorCode());
        assertEquals("{\"result\":{\"answer\":42.0,\"names\":[\"a\",\"b\"]},\"requests\":[{\"capability\":\"uuk.page.open\",\"payload\":{\"page\":\"demo\"}}]}", result.payloadJson());
    }

    @Test void providesNoJavaFilesystemNetworkOrHostReflection() throws Exception {
        for (String script : List.of("java.lang.System.exit(0)", "Packages.java.lang.Runtime.getRuntime()",
                "Java.type('java.lang.System')", "new JavaAdapter(java.lang.Runnable,{})",
                "load('file:///tmp/x.js')", "readFile('secret')", "fetch('https://example.com')",
                "uuk.request.getClass()", "input.getClass()", "uuk.request.constructor('return java.lang.System')()")) {
            assertFalse(run(script).success(), script);
        }
        assertTrue(run("({java:typeof java, packages:typeof Packages, fetch:typeof fetch})").payloadJson().contains("undefined"));
    }

    @Test void rejectsFunctionsCyclesExcessDepthAndHugeOutput() throws Exception {
        for (String script : List.of("(function(){})", "var a={};a.a=a;a", "var a={};for(var i=0;i<40;i++)a={a:a};a",
                "'x'.repeat(100000)", "new Date()", "({toJSON:function(){return 'hidden'}})")) {
            assertFalse(run(script).success(), script);
        }
    }

    @Test void rejectsTooManyIntentsAndLargeInputBeforeLaunch() throws Exception {
        try (var worker = new ScriptWorkerSupervisor(jar(), limits(5000, 65536, 1))) {
            assertEquals("REQUEST_LIMIT", worker.submit("uuk.request('a',{});uuk.request('b',{})", "{}").get(7, TimeUnit.SECONDS).errorCode());
            assertEquals("INPUT_LIMIT", worker.submit("x".repeat(65537), "{}").join().errorCode());
            assertEquals("INPUT_LIMIT", worker.submit("1", "中".repeat(20000)).join().errorCode());
            assertEquals(0, worker.activeProcessCount());
        }
    }

    @Test void killsInfiniteLoopAndExpensiveBuiltinWithinHardDeadline() throws Exception {
        for (String source : List.of("while(true){}", "new RegExp('(a+)+$').test('a'.repeat(20000)+'!')")) {
            long started = System.nanoTime();
            try (var worker = new ScriptWorkerSupervisor(jar(), limits(1000, 65536, 16))) {
                var result = worker.submit(source, "{}").get(4, TimeUnit.SECONDS);
                assertFalse(result.success());
                assertEquals("TIMEOUT", result.errorCode());
            }
            assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 4000);
        }
    }

    @Test void getterReentrancyCannotBypassRequestBudgetEvenIfCaught() throws Exception {
        try (var worker = new ScriptWorkerSupervisor(jar(), limits(5000, 65536, 1))) {
            for (String source : List.of(
                    "var p={get x(){uuk.request('inner',{});return 1;}};uuk.request('outer',p);null;",
                    "var p={get x(){try{uuk.request('inner',{})}catch(e){}return 1;}};try{uuk.request('outer',p)}catch(e){};null;",
                    "uuk.request('first',{});({get x(){try{uuk.request('second',{})}catch(e){}return 1;}})")) {
                var result = worker.submit(source, "{}").get(7, TimeUnit.SECONDS);
                assertFalse(result.success(), source);
                assertEquals("REQUEST_LIMIT", result.errorCode());
                assertEquals("null", result.payloadJson());
            }
        }
    }

    @Test void reentrantRequestsKeepReservedIndicesAndCallOrder() throws Exception {
        var result = run("var inner;var outer=uuk.request('outer',{get x(){inner=uuk.request('inner',{});return 1;}});({outer:outer.index,inner:inner.index})");
        assertTrue(result.success(), result.errorCode());
        assertTrue(result.payloadJson().contains("\"outer\":0,\"inner\":1"));
        assertTrue(result.payloadJson().indexOf("\"capability\":\"outer\"")
                < result.payloadJson().indexOf("\"capability\":\"inner\""));
    }

    @Test void caughtPayloadGetterFailureNeverReturnsPartialIntents() throws Exception {
        var result = run("try{uuk.request('broken',{get x(){throw new Error('stop')}})}catch(e){};uuk.request('other',{});null");
        assertFalse(result.success());
        assertEquals("SCRIPT_ERROR", result.errorCode());
        assertEquals("null", result.payloadJson());
    }

    @Test void queueIsBoundedAndCloseTerminatesRunningAndQueuedWork() throws Exception {
        var worker = new ScriptWorkerSupervisor(jar(), limits(10000, 65536, 16));
        try {
            var first = worker.submit("while(true){}", "{}");
            awaitRunning(worker);
            var queued = worker.submit("42", "{}");
            assertEquals("BUSY", worker.submit("42", "{}").join().errorCode());
            worker.close();
            assertEquals("CLOSED", first.join().errorCode());
            assertEquals("CLOSED", queued.join().errorCode());
            assertEquals(0, worker.activeProcessCount());
            assertEquals("CLOSED", worker.submit("42", "{}").join().errorCode());
        } finally { worker.close(); }
    }

    @Test void cancellationStopsTheRunningProcess() throws Exception {
        try (var worker = new ScriptWorkerSupervisor(jar(), limits(10000, 65536, 16))) {
            var task = worker.submit("while(true){}", "{}");
            awaitRunning(worker);
            task.cancel(true);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (worker.activeProcessCount() != 0 && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals(0, worker.activeProcessCount());
            assertTrue(worker.submit("42", "{}").get(7, TimeUnit.SECONDS).success());
        }
    }

    @Test void rejectsOversizedAndTruncatedProtocolFrames() throws Exception {
        for (int length : List.of(-1, 65537, 20)) {
            Process process = startRawWorker();
            try {
                try (DataOutputStream out = new DataOutputStream(process.getOutputStream())) {
                    out.writeInt(0x55554b31); out.writeInt(65536); out.writeInt(8192); out.writeInt(16); out.writeInt(1000);
                    out.writeInt(length); out.writeByte(0); out.flush();
                }
                try (DataInputStream in = new DataInputStream(process.getInputStream())) {
                    assertEquals(0x55554b31, in.readInt()); assertFalse(in.readBoolean());
                    int size = in.readInt(); assertTrue(size > 0 && size < 100);
                    assertEquals("PROTOCOL_ERROR", new String(in.readNBytes(size), StandardCharsets.UTF_8));
                }
                assertTrue(process.waitFor(3, TimeUnit.SECONDS));
            } finally { process.destroyForcibly(); process.waitFor(3, TimeUnit.SECONDS); }
        }
    }

    @Test void workerJarHasNoMinecraftOrLatvianRuntimeAndContainsLicense() throws Exception {
        try (var zip = new java.util.zip.ZipFile(jar().toFile())) {
            assertNotNull(zip.getEntry("LICENSE-RHINO.txt"));
            assertNotNull(zip.getEntry("THIRD-PARTY-NOTICES.md"));
            assertTrue(zip.stream().noneMatch(entry -> entry.getName().startsWith("net/minecraft/")
                    || entry.getName().startsWith("dev/latvian/") || entry.getName().startsWith("net/neoforged/")));
        }
    }

    @Test void exitsWhenItsRecordedParentDoesNotExist() throws Exception {
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "javaw.exe" : "java").toString(),
                "-Xmx64m", "-jar", jar().toString(), Long.toString(Long.MAX_VALUE), "0")
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            // No stdin frame: the independent watcher must terminate even a blocked read.
            assertTrue(process.waitFor(3, TimeUnit.SECONDS));
            assertEquals(125, process.exitValue());
        } finally { process.destroyForcibly(); process.waitFor(3, TimeUnit.SECONDS); }
    }

    private static Process startRawWorker() throws IOException {
        return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "javaw.exe" : "java").toString(),
                "-Xmx64m", "-jar", jar().toString(), Long.toString(ProcessHandle.current().pid()),
                Long.toString(ProcessHandle.current().info().startInstant().orElseThrow().toEpochMilli()))
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }
    private static void awaitRunning(ScriptWorkerSupervisor worker) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (worker.activeProcessCount() == 0 && System.nanoTime() < deadline) Thread.sleep(10);
        assertEquals(1, worker.activeProcessCount());
    }
}
