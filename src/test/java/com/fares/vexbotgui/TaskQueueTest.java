package com.fares.vexbotgui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaskQueueTest {
    @Test void runsAfterTheDelayAndInOrder() {
        TaskQueue q = new TaskQueue();
        List<String> ran = new ArrayList<>();
        q.add(() -> ran.add("b"), 2);
        q.add(() -> ran.add("a"), 1);
        q.tick(e -> fail());
        assertEquals(List.of("a"), ran);
        q.tick(e -> fail());
        assertEquals(List.of("a", "b"), ran);
        assertEquals(0, q.size());
    }

    @Test void clearDropsEverythingPending() {                 // disconnect / join another server
        TaskQueue q = new TaskQueue();
        List<String> ran = new ArrayList<>();
        q.add(() -> ran.add("old"), 3);
        q.clear();
        for (int i = 0; i < 10; i++) q.tick(e -> fail());
        assertTrue(ran.isEmpty());
    }

    @Test void aFailingTaskDoesNotStopTheOthers() {            // used to be able to crash the client tick
        TaskQueue q = new TaskQueue();
        List<String> ran = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        q.add(() -> { throw new IllegalStateException("boom"); }, 1);
        q.add(() -> ran.add("after"), 1);
        q.tick(e -> errors.add(e.getMessage()));
        assertEquals(List.of("after"), ran);
        assertEquals(List.of("boom"), errors);
    }

    @Test void clearInsideATaskStopsTasksDueInTheSameTick() {
        TaskQueue q = new TaskQueue();
        List<String> ran = new ArrayList<>();
        q.add(q::clear, 1);
        q.add(() -> ran.add("must not run"), 1);
        q.tick(e -> fail());
        assertTrue(ran.isEmpty());
        assertEquals(0, q.size());
    }

    @Test void tasksAddedWhileRunningWaitForTheNextTick() {
        TaskQueue q = new TaskQueue();
        List<String> ran = new ArrayList<>();
        q.add(() -> q.add(() -> ran.add("child"), 1), 1);
        q.tick(e -> fail());
        assertTrue(ran.isEmpty());
        q.tick(e -> fail());
        assertEquals(List.of("child"), ran);
    }

    @Test void zeroOrNegativeDelayStillWaitsOneTick() {
        TaskQueue q = new TaskQueue();
        List<String> ran = new ArrayList<>();
        q.add(() -> ran.add("x"), 0);
        q.add(() -> ran.add("y"), -5);
        assertEquals(2, q.size());
        q.tick(e -> fail());
        assertEquals(List.of("x", "y"), ran);
    }

    private static void fail() { throw new AssertionError("unexpected error callback"); }
}
