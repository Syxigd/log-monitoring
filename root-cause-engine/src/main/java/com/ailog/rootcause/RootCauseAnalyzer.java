package com.ailog.rootcause;

import com.ailog.context.ContextPackage;
import org.jeasy.rules.api.Rule;

public interface RootCauseAnalyzer extends Rule {

    RootCause rootCause();

    boolean evaluate(ContextPackage context);

    RootCauseReport execute(ContextPackage context);
}
