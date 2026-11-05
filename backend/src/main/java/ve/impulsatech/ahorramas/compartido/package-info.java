/**
 * Kernel compartido: tipos de valor del dominio financiero (montos en céntimos, tasa BCV), reloj,
 * errores de negocio, auditoría, cifrado, seguridad JWT/step-up y cliente HTTP del Hub de integraciones.
 * Es un módulo abierto: lo puede usar cualquier otro módulo, pero él no depende de ninguno.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Kernel compartido",
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package ve.impulsatech.ahorramas.compartido;
