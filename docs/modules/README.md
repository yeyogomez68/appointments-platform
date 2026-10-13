# Cómo crear un módulo

El backend es un monolito modular con Spring Modulith. Cada paquete directo bajo `com.plataformacitas` es un módulo; sus subpaquetes son internos y ningún otro módulo puede usarlos. `shared` es la excepción: es un módulo abierto (`@ApplicationModule(type = OPEN)` en `shared/package-info.java`) porque todos lo usan.

## Estructura de un módulo

- **API pública:** las clases en la raíz del paquete del módulo (`com.plataformacitas.<modulo>`). Es lo único que otros módulos pueden importar: servicios de fachada, DTOs y eventos.
- **Internos:** todo lo que está en subpaquetes. Spring Modulith rompe el build si otro módulo depende de ellos.
- **Migraciones:** `backend/src/main/resources/db/migration/V{n}__{modulo}_{descripcion}.sql`. Toda tabla con `business_id` trae su política RLS en la misma migración.
- **Dependencias entre módulos:** sin ciclos. Por ejemplo, `identity` depende de `platform`, nunca al revés.

## Hexagonal completa

Para módulos con reglas de negocio ricas: `agenda` (bundle 2) y `cash` (bundle 3).

```
com.plataformacitas.<modulo>/
  <Fachada>.java                 API pública
  domain/                        modelo y reglas puras (sin Spring ni JPA)
  application/                   casos de uso
  application/port/out/          puertos de salida (interfaces)
  adapter/in/web/                controladores REST
  adapter/out/persistence/       implementación de los puertos con JPA/JDBC
```

## Módulo ligero

Para módulos mayormente CRUD: `identity`, `platform`, `catalog`, `staff` y `customers`.

```
com.plataformacitas.<modulo>/
  <Fachada>.java                 API pública
  internal/                      entidades, repositorios, controladores y servicios internos
```

## Reglas verificadas

`ModularityTest` (Spring Modulith):

- `ApplicationModules.of(CitasApplication.class).verify()`: sin ciclos entre módulos y sin acceso a subpaquetes internos de otro módulo.

`ArchitectureRulesTest` (ArchUnit, solo clases de producción):

- `DOMAIN_IS_PURE`: nada en `..domain..` depende de `org.springframework..`, `jakarta.persistence..`, `..application..` ni `..adapter..`. Se prueba contra el fixture `archfixtures.baddomain`, que debe fallar.
- `APPLICATION_WITHOUT_ADAPTERS`: nada en `..application..` depende de `..adapter..`.
- `VERTICAL_NAMES_STAY_IN_VERTICAL`: ninguna clase fuera de `com.plataformacitas.vertical..` lleva `Peluqueria` en el nombre.

Las dos primeras usan `allowEmptyShould(true)` porque aún no existen paquetes `domain` ni `application`.
