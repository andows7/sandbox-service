package com.huixue.sandbox.strategy.c;

import com.huixue.sandbox.infrastructure.docker.DockerContainerManager;
import com.huixue.sandbox.strategy.cpp.CppExecutorStrategy;
import org.springframework.stereotype.Component;

/** C and C++ share the native executable protocol; compilation remains language-specific. */
@Component("cExecutorStrategy")
public class CExecutorStrategy extends CppExecutorStrategy {
    public CExecutorStrategy(DockerContainerManager manager) { super(manager); }
}
