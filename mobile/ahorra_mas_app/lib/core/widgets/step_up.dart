import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../features/auth/domain/auth_models.dart';
import '../api/api_exception.dart';
import '../di/providers.dart';
import 'texto_enfasis.dart';

/// Pide confirmación reforzada (step-up) y devuelve el token, o `null` si el usuario cancela.
///
/// * `conOtp: false` → nivel `pin` (reglas, sello, retiros, IA, saldo rápido).
/// * `conOtp: true` → nivel `pin_otp` (pagos de TDC): primero solicita el OTP por SMS.
Future<StepUp?> solicitarStepUp(
  BuildContext context, {
  required String motivo,
  bool conOtp = false,
}) {
  return showModalBottomSheet<StepUp>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (_) => _HojaStepUp(motivo: motivo, conOtp: conOtp),
  );
}

class _HojaStepUp extends ConsumerStatefulWidget {
  const _HojaStepUp({required this.motivo, required this.conOtp});

  final String motivo;
  final bool conOtp;

  @override
  ConsumerState<_HojaStepUp> createState() => _HojaStepUpState();
}

class _HojaStepUpState extends ConsumerState<_HojaStepUp> {
  final _pin = TextEditingController();
  final _otp = TextEditingController();
  DesafioOtp? _desafio;
  String? _error;
  bool _ocupado = false;

  @override
  void initState() {
    super.initState();
    if (widget.conOtp) _pedirOtp();
  }

  @override
  void dispose() {
    _pin.dispose();
    _otp.dispose();
    super.dispose();
  }

  Future<void> _pedirOtp() async {
    setState(() {
      _ocupado = true;
      _error = null;
    });
    try {
      final d = await ref.read(authRepositoryProvider).solicitarOtp('pago');
      if (mounted) setState(() => _desafio = d);
    } catch (e) {
      if (mounted) setState(() => _error = mensajeDeError(e));
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  Future<void> _confirmar() async {
    final pin = _pin.text.trim();
    if (pin.length < 4) {
      setState(() => _error = 'El PIN tiene entre 4 y 6 dígitos.');
      return;
    }
    if (widget.conOtp && (_desafio == null || _otp.text.trim().length < 4)) {
      setState(() => _error = 'Escribe el código que te enviamos por SMS.');
      return;
    }
    setState(() {
      _ocupado = true;
      _error = null;
    });
    try {
      final r = await ref.read(authRepositoryProvider).stepUp(
            pin: pin,
            idDesafio: widget.conOtp ? _desafio!.idDesafio : null,
            otp: widget.conOtp ? _otp.text.trim() : null,
          );
      if (mounted) Navigator.of(context).pop(r);
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = mensajeDeError(e);
          _ocupado = false;
          _pin.clear();
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: EdgeInsets.fromLTRB(24, 0, 24, 24 + MediaQuery.viewInsetsOf(context).bottom),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Icon(widget.conOtp ? Icons.verified_user_outlined : Icons.lock_outline, color: cs.primary),
              const SizedBox(width: 8),
              Expanded(
                child: Text(widget.conOtp ? 'Confirma con PIN y código SMS' : 'Confirma con tu PIN',
                    style: t.titleLarge),
              ),
            ],
          ),
          const SizedBox(height: 8),
          TextoEnfasis(widget.motivo, style: t.bodyMedium?.copyWith(color: cs.onSurfaceVariant)),
          const SizedBox(height: 16),
          if (widget.conOtp && _desafio?.otpDemo != null)
            Container(
              key: const ValueKey('otp-demo'),
              margin: const EdgeInsets.only(bottom: 12),
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: cs.tertiaryContainer,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Row(
                children: [
                  Icon(Icons.developer_mode, size: 18, color: cs.onTertiaryContainer),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text('Modo demo - OTP enviado por SMS: ${_desafio!.otpDemo}',
                        style: TextStyle(color: cs.onTertiaryContainer)),
                  ),
                  TextButton(
                    onPressed: () => _otp.text = _desafio!.otpDemo!,
                    child: const Text('Usar'),
                  ),
                ],
              ),
            ),
          TextField(
            key: const ValueKey('stepup-pin'),
            controller: _pin,
            autofocus: true,
            obscureText: true,
            keyboardType: TextInputType.number,
            maxLength: 6,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            decoration: const InputDecoration(labelText: 'PIN', counterText: ''),
            onSubmitted: (_) => widget.conOtp ? null : _confirmar(),
          ),
          if (widget.conOtp) ...[
            const SizedBox(height: 12),
            TextField(
              key: const ValueKey('stepup-otp'),
              controller: _otp,
              keyboardType: TextInputType.number,
              maxLength: 8,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              decoration: InputDecoration(
                labelText: 'Código SMS (OTP)',
                counterText: '',
                suffixIcon: TextButton(
                  onPressed: _ocupado ? null : _pedirOtp,
                  child: const Text('Reenviar'),
                ),
              ),
            ),
          ],
          if (_error != null) ...[
            const SizedBox(height: 8),
            Text(_error!, style: TextStyle(color: cs.error)),
          ],
          const SizedBox(height: 16),
          FilledButton(
            key: const ValueKey('stepup-confirmar'),
            onPressed: _ocupado ? null : _confirmar,
            child: _ocupado
                ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2.5))
                : const Text('Confirmar'),
          ),
        ],
      ),
    );
  }
}
