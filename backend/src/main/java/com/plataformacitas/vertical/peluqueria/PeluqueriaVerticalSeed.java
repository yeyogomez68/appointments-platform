package com.plataformacitas.vertical.peluqueria;

import com.plataformacitas.platform.Businesses;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Valores iniciales del vertical peluquería: ajustes de agenda y caja, y etiquetas de la interfaz. */
@Component
public class PeluqueriaVerticalSeed implements Businesses.VerticalSeed {

    private static final Defaults DEFAULTS = new Defaults(15, 0, true, 0, true, "EMPLOYEE", 10, Map.of(
            "resource", "Empleado",
            "resourcePlural", "Empleados",
            "service", "Servicio",
            "servicePlural", "Servicios",
            "customer", "Cliente",
            "customerPlural", "Clientes"));

    @Override
    public String vertical() {
        return "PELUQUERIA";
    }

    @Override
    public Defaults defaults() {
        return DEFAULTS;
    }
}
