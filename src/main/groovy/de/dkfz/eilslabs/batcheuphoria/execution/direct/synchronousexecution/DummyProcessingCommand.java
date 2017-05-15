/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.eilslabs.batcheuphoria.execution.direct.synchronousexecution;

import de.dkfz.eilslabs.batcheuphoria.jobs.ProcessingCommands;

/**
 */
public class DummyProcessingCommand extends ProcessingCommands {
    private String text;

    public DummyProcessingCommand(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return "Dummy: " + text;
    }
}
