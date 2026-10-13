package com.plataformacitas;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void modulesRespectBoundaries() {
        ApplicationModules.of(CitasApplication.class).verify();
    }
}
