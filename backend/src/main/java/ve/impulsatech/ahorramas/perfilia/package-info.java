/**
 * M4 · Asistente de perfil de riesgo (F4). Clasifica conservador / moderado / dinámico con reglas
 * versionadas, pide al LLM solo la redacción (con datos seudonimizados) y NUNCA ejecuta nada sin la
 * aprobación del usuario con huella/PIN. En producción el clasificador vive en un contenedor Python;
 * aquí es un adaptador intercambiable detrás del mismo puerto.
 */
@org.springframework.modulith.ApplicationModule(displayName = "M4 · Asistente de perfil de riesgo (IA)")
package ve.impulsatech.ahorramas.perfilia;
