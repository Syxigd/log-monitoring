package com.ailog.orchestrator.service;

import com.ailog.common.event.Event;
import com.ailog.orchestrator.state.StateMachine;
import com.ailog.orchestrator.state.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationService.class);

    private final StateMachine stateMachine;

    public OrchestrationService() {
        this.stateMachine = new StateMachine();
    }

    public void onEvent(Event event) {
        log.info("Received event: {} from {}", event.getType(), event.getSource());

        if (stateMachine.transitionTo(WorkflowState.CONTEXT)) {
            log.info("Transitioned to CONTEXT state");
        }
    }

    public WorkflowState getCurrentState() {
        return stateMachine.getCurrentState();
    }
}
