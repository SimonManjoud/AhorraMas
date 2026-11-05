import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/di/providers.dart';
import '../../../core/session/session_controller.dart';
import '../../../core/storage/almacen_seguro.dart';
import '../../../core/util/fechas.dart';
import '../../../core/widgets/comunes.dart';
import '../domain/auth_models.dart';

/// Registro en 3 pasos: datos personales → contacto → PIN. Luego el router lleva al KYC.
class RegistroScreen extends ConsumerStatefulWidget {
  const RegistroScreen({super.key});

  @override
  ConsumerState<RegistroScreen> createState() => _RegistroScreenState();
}

class _RegistroScreenState extends ConsumerState<RegistroScreen> {
  final _formularios = [GlobalKey<FormState>(), GlobalKey<FormState>(), GlobalKey<FormState>()];
  int _paso = 0;
  bool _ocupado = false;

  String _nacionalidad = 'V';
  final _cedula = TextEditingController();
  final _primerNombre = TextEditingController();
  final _segundoNombre = TextEditingController();
  final _primerApellido = TextEditingController();
  final _segundoApellido = TextEditingController();
  DateTime? _nacimiento;
  final _correo = TextEditingController();
  final _telefono = TextEditingController();
  final _pin = TextEditingController();
  final _pin2 = TextEditingController();
  bool _aceptaTerminos = false;

  @override
  void dispose() {
    for (final c in [
      _cedula, _primerNombre, _segundoNombre, _primerApellido, _segundoApellido, _correo, _telefono, _pin, _pin2,
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  bool get _mayorDeEdad {
    final n = _nacimiento;
    if (n == null) return false;
    final hoy = DateTime.now();
    final cumple18 = DateTime(n.year + 18, n.month, n.day);
    return !cumple18.isAfter(hoy);
  }

  Future<void> _elegirFecha() async {
    final hoy = DateTime.now();
    final f = await showDatePicker(
      context: context,
      initialDate: _nacimiento ?? DateTime(hoy.year - 25, 1, 1),
      firstDate: DateTime(1920),
      lastDate: hoy,
      helpText: 'Fecha de nacimiento',
    );
    if (f != null) setState(() => _nacimiento = f);
  }

  Future<void> _siguiente() async {
    if (!_formularios[_paso].currentState!.validate()) return;
    if (_paso == 0 && !_mayorDeEdad) {
      mostrarMensaje(context, 'Debes ser mayor de edad para registrarte.');
      return;
    }
    if (_paso < 2) {
      setState(() => _paso++);
      return;
    }
    if (!_aceptaTerminos) {
      mostrarMensaje(context, 'Debes aceptar los términos y la política de privacidad.');
      return;
    }
    await _registrar();
  }

  Future<void> _registrar() async {
    setState(() => _ocupado = true);
    try {
      final dispositivo = await ref.read(dispositivoProvider.future);
      String? opcional(TextEditingController c) => c.text.trim().isEmpty ? null : c.text.trim();
      final sesion = await ref.read(authRepositoryProvider).registrar(SolicitudRegistro(
            nacionalidadDoc: _nacionalidad,
            cedula: int.parse(_cedula.text.trim()),
            primerNombre: _primerNombre.text.trim(),
            segundoNombre: opcional(_segundoNombre),
            primerApellido: _primerApellido.text.trim(),
            segundoApellido: opcional(_segundoApellido),
            fechaNacimiento: _nacimiento!,
            correo: _correo.text.trim(),
            telefonoMovil: _telefono.text.trim(),
            pin: _pin.text,
            dispositivo: dispositivo,
          ));
      final almacen = ref.read(almacenSeguroProvider);
      await almacen.escribir(ClavesAlmacen.ultimaCedula, _cedula.text.trim());
      await almacen.escribir(ClavesAlmacen.ultimaNacionalidad, _nacionalidad);
      ref.read(sessionProvider.notifier).iniciar(sesion); // el router lleva a /kyc
    } catch (e) {
      if (mounted) {
        mostrarError(context, e);
        if (e is ApiException && e.statusCode == 409) setState(() => _paso = 0);
      }
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  String? _requerido(String? v) => (v == null || v.trim().isEmpty) ? 'Requerido' : null;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Crea tu cuenta')),
      body: Stepper(
        currentStep: _paso,
        onStepContinue: _ocupado ? null : _siguiente,
        onStepCancel: _paso == 0 || _ocupado ? null : () => setState(() => _paso--),
        onStepTapped: (i) {
          if (i < _paso) setState(() => _paso = i);
        },
        controlsBuilder: (context, details) => Padding(
          padding: const EdgeInsets.only(top: 16),
          child: Row(
            children: [
              FilledButton(
                onPressed: details.onStepContinue,
                child: _ocupado
                    ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                    : Text(_paso == 2 ? 'Crear cuenta' : 'Continuar'),
              ),
              const SizedBox(width: 8),
              if (details.onStepCancel != null)
                TextButton(onPressed: details.onStepCancel, child: const Text('Atrás')),
            ],
          ),
        ),
        steps: [
          Step(
            title: const Text('Tus datos'),
            isActive: _paso >= 0,
            state: _paso > 0 ? StepState.complete : StepState.indexed,
            content: Form(
              key: _formularios[0],
              child: Column(
                children: [
                  Row(
                    children: [
                      SegmentedButton<String>(
                        segments: const [
                          ButtonSegment(value: 'V', label: Text('V')),
                          ButtonSegment(value: 'E', label: Text('E')),
                        ],
                        selected: {_nacionalidad},
                        showSelectedIcon: false,
                        onSelectionChanged: (s) => setState(() => _nacionalidad = s.first),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: TextFormField(
                          controller: _cedula,
                          keyboardType: TextInputType.number,
                          inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(9)],
                          decoration: const InputDecoration(labelText: 'Cédula'),
                          validator: (v) => (int.tryParse(v ?? '') ?? 0) < 100000 ? 'Cédula inválida' : null,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: _primerNombre,
                    textCapitalization: TextCapitalization.words,
                    decoration: const InputDecoration(labelText: 'Primer nombre'),
                    validator: _requerido,
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: _segundoNombre,
                    textCapitalization: TextCapitalization.words,
                    decoration: const InputDecoration(labelText: 'Segundo nombre (opcional)'),
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: _primerApellido,
                    textCapitalization: TextCapitalization.words,
                    decoration: const InputDecoration(labelText: 'Primer apellido'),
                    validator: _requerido,
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: _segundoApellido,
                    textCapitalization: TextCapitalization.words,
                    decoration: const InputDecoration(labelText: 'Segundo apellido (opcional)'),
                  ),
                  const SizedBox(height: 12),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.cake_outlined),
                    title: const Text('Fecha de nacimiento'),
                    subtitle: Text(_nacimiento == null ? 'Elegir fecha' : Fechas.larga(_nacimiento)),
                    trailing: _nacimiento != null && !_mayorDeEdad
                        ? Text('Menor de edad', style: TextStyle(color: Theme.of(context).colorScheme.error))
                        : null,
                    onTap: _elegirFecha,
                  ),
                ],
              ),
            ),
          ),
          Step(
            title: const Text('Contacto'),
            isActive: _paso >= 1,
            state: _paso > 1 ? StepState.complete : StepState.indexed,
            content: Form(
              key: _formularios[1],
              child: Column(
                children: [
                  TextFormField(
                    controller: _correo,
                    keyboardType: TextInputType.emailAddress,
                    decoration: const InputDecoration(labelText: 'Correo electrónico'),
                    validator: (v) =>
                        RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(v?.trim() ?? '') ? null : 'Correo inválido',
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: _telefono,
                    keyboardType: TextInputType.phone,
                    inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(11)],
                    decoration: const InputDecoration(labelText: 'Teléfono móvil', hintText: '04141234567'),
                    validator: (v) =>
                        RegExp(r'^04(12|14|16|24|26|22)\d{7}$').hasMatch(v ?? '') ? null : 'Ej. 04141234567',
                  ),
                  const SizedBox(height: 8),
                  Text('Te enviaremos por SMS los códigos para confirmar pagos.',
                      style: Theme.of(context).textTheme.bodySmall),
                ],
              ),
            ),
          ),
          Step(
            title: const Text('Tu PIN'),
            isActive: _paso >= 2,
            content: Form(
              key: _formularios[2],
              child: Column(
                children: [
                  TextFormField(
                    controller: _pin,
                    obscureText: true,
                    keyboardType: TextInputType.number,
                    inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(6)],
                    decoration: const InputDecoration(labelText: 'PIN (4 a 6 dígitos)'),
                    validator: (v) {
                      final p = v ?? '';
                      if (p.length < 4) return 'Entre 4 y 6 dígitos';
                      if (RegExp(r'^(\d)\1+$').hasMatch(p)) return 'Evita repetir el mismo dígito';
                      return null;
                    },
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: _pin2,
                    obscureText: true,
                    keyboardType: TextInputType.number,
                    inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(6)],
                    decoration: const InputDecoration(labelText: 'Repite tu PIN'),
                    validator: (v) => v != _pin.text ? 'Los PIN no coinciden' : null,
                  ),
                  const SizedBox(height: 8),
                  CheckboxListTile(
                    contentPadding: EdgeInsets.zero,
                    value: _aceptaTerminos,
                    onChanged: (v) => setState(() => _aceptaTerminos = v ?? false),
                    title: const Text('Acepto los términos y la política de privacidad'),
                    controlAffinity: ListTileControlAffinity.leading,
                  ),
                  const AvisoSinClaves(compacto: true),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
