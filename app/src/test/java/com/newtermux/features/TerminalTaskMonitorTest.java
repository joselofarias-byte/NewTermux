package com.newtermux.features;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TerminalTaskMonitorTest {

    @Test
    public void recognizesDpkgNodeConfiguration() {
        TerminalTaskMonitor.Analysis a =
            TerminalTaskMonitor.analyzeLine("Setting up node-esrecurse (4.3.0-2) ...");

        assertNotNull(a);
        assertEquals("apt", a.kind);
        assertEquals("Instalando Node.js / NPM", a.title);
        assertEquals("Configurando paquetes", a.phase);
        assertEquals("node-esrecurse", a.item);
        assertTrue(a.countItem);
        assertFalse(a.completed);
    }

    @Test
    public void aptSummaryUsesCurrentTransactionTotal() {
        assertEquals(20, TerminalTaskMonitor.aptPackageTotal(
            "0 upgraded, 20 newly installed, 0 to remove and 3 not upgraded."));
        assertEquals(4, TerminalTaskMonitor.aptPackageTotal(
            "0 upgraded, 4 newly installed, 0 to remove and 3 not upgraded."));
        assertEquals(7, TerminalTaskMonitor.aptPackageTotal(
            "3 upgraded, 4 newly installed, 0 to remove and 0 not upgraded."));
        assertEquals(-1, TerminalTaskMonitor.aptPackageTotal(
            "Reading package lists... Done"));
    }

    @Test
    public void recognizesShellCommandNotFoundAsScriptError() {
        assertTrue(TerminalTaskMonitor.isScriptCommandError(
            "/bin/bash: line 13: llvm-19-tools: command not found"));
        assertFalse(TerminalTaskMonitor.isScriptCommandError(
            "llvm-19-tools is already the newest version"));
    }

    @Test
    public void recognizesGitProgress() {
        TerminalTaskMonitor.Analysis a =
            TerminalTaskMonitor.analyzeLine("Receiving objects: 67% (670/1000), 8.10 MiB | 5.00 MiB/s");

        assertNotNull(a);
        assertEquals("git", a.kind);
        assertEquals("Recibiendo objetos", a.phase);
        assertEquals(67, a.explicitPercent);
    }

    @Test
    public void recognizesGradleCompletionAndFailure() {
        TerminalTaskMonitor.Analysis ok =
            TerminalTaskMonitor.analyzeLine("BUILD SUCCESSFUL in 42s");
        assertNotNull(ok);
        assertTrue(ok.completed);
        assertFalse(ok.failed);
        assertEquals(100, ok.explicitPercent);

        TerminalTaskMonitor.Analysis failed =
            TerminalTaskMonitor.analyzeLine("BUILD FAILED in 9s");
        assertNotNull(failed);
        assertTrue(failed.completed);
        assertTrue(failed.failed);
    }

    @Test
    public void stripsAnsiBeforeClassification() {
        TerminalTaskMonitor.Analysis a =
            TerminalTaskMonitor.analyzeLine("\u001B[32mUnpacking node-debug (4.3.4+~cs4.1.7-1) ...\u001B[0m");

        assertNotNull(a);
        assertEquals("node-debug", a.item);
        assertEquals("Desempaquetando paquetes", a.phase);
    }

    @Test
    public void recognizesOneTouchTerminalStatus() {
        TerminalTaskMonitor.Analysis failed =
            TerminalTaskMonitor.analyzeLine("UN_TOQUE_FAIL exit=1");
        assertNotNull(failed);
        assertTrue(failed.completed);
        assertTrue(failed.failed);
        assertEquals("Preparando NewTermux", failed.title);

        TerminalTaskMonitor.Analysis ok =
            TerminalTaskMonitor.analyzeLine("UN_TOQUE_OK");
        assertNotNull(ok);
        assertTrue(ok.completed);
        assertFalse(ok.failed);
        assertEquals(100, ok.explicitPercent);
    }

    @Test
    public void promptCannotDowngradeExplicitFailure() {
        assertTrue(TerminalTaskMonitor.mergeFailureState(true, false));
        assertTrue(TerminalTaskMonitor.mergeFailureState(false, true));
        assertFalse(TerminalTaskMonitor.mergeFailureState(false, false));
    }

    @Test
    public void ignoresOrdinaryShellText() {
        TerminalTaskMonitor.Analysis a =
            TerminalTaskMonitor.analyzeLine("hello from the shell");
        assertEquals(null, a);
    }
}
