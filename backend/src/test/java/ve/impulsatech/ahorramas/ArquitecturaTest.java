package ve.impulsatech.ahorramas;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifica que el código cumpla la arquitectura obligatoria:
 * <ul>
 *   <li>Monolito modular: los módulos solo se usan entre sí por su paquete {@code api} o por eventos, sin ciclos.</li>
 *   <li>Hexagonal: el dominio es puro; la aplicación no conoce adaptadores; los adaptadores de entrada no usan los de salida.</li>
 *   <li>R-01: el dominio nunca usa punto flotante para dinero.</li>
 * </ul>
 */
class ArquitecturaTest {

    static final ApplicationModules MODULOS = ApplicationModules.of(AhorraMasApplication.class);
    static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("ve.impulsatech.ahorramas");

    @Test
    void losModulosRespetanSusLimitesYNoTienenCiclos() {
        MODULOS.verify();
    }

    @Test
    void generaLaDocumentacionDeModulos() {
        new Documenter(MODULOS).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml().writeModuleCanvases();
    }

    @Test
    void elDominioEsPuroSinFrameworksNiInfraestructura() {
        noClasses().that().resideInAPackage("..dominio..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "java.sql..", "tools.jackson..",
                        "..aplicacion..", "..adaptador..", "..infraestructura..")
                .check(CLASES);
    }

    @Test
    void laAplicacionNoDependeDeLosAdaptadores() {
        noClasses().that().resideInAPackage("..aplicacion..")
                .should().dependOnClassesThat().resideInAPackage("..adaptador..")
                .check(CLASES);
    }

    @Test
    void losAdaptadoresDeEntradaNoUsanLosDeSalida() {
        noClasses().that().resideInAPackage("..adaptador.entrada..")
                .should().dependOnClassesThat().resideInAPackage("..adaptador.salida..")
                .check(CLASES);
    }

    @Test
    void elDominioNuncaUsaPuntoFlotanteParaMontos() {
        noFields().that().areDeclaredInClassesThat().resideInAPackage("..dominio..")
                .should().haveRawType(double.class).orShould().haveRawType(float.class)
                .orShould().haveRawType(Double.class).orShould().haveRawType(Float.class)
                .check(CLASES);
    }
}
