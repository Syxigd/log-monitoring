package com.ailog.orchestrator.state;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public class StateMachine {

    private WorkflowState currentState;

    private static final Map<WorkflowState, Set<WorkflowState>> transitions = new EnumMap<>(WorkflowState.class);

    static {
        transitions.put(WorkflowState.OBSERVE, Set.of(WorkflowState.CONTEXT, WorkflowState.FAILED));
        transitions.put(WorkflowState.CONTEXT, Set.of(WorkflowState.DIAGNOSE, WorkflowState.FAILED));
        transitions.put(WorkflowState.DIAGNOSE, Set.of(WorkflowState.PLAN, WorkflowState.FAILED));
        transitions.put(WorkflowState.PLAN, Set.of(WorkflowState.EXECUTE, WorkflowState.FAILED));
        transitions.put(WorkflowState.EXECUTE, Set.of(WorkflowState.VALIDATE, WorkflowState.FAILED));
        transitions.put(WorkflowState.VALIDATE, Set.of(WorkflowState.COMPLETED, WorkflowState.FAILED));
    }

    public StateMachine() {
        this.currentState = WorkflowState.OBSERVE;
    }

    public synchronized boolean transitionTo(WorkflowState target) {
        Set<WorkflowState> allowed = transitions.get(currentState);
        if (allowed != null && allowed.contains(target)) {
            currentState = target;
            return true;
        }
        return false;
    }

    public WorkflowState getCurrentState() {
        return currentState;
    }

    public boolean canTransitionTo(WorkflowState target) {
        Set<WorkflowState> allowed = transitions.get(currentState);
        return allowed != null && allowed.contains(target);
    }
}
