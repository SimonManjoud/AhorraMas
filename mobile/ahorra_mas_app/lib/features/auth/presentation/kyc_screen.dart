import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import '../../../core/di/providers.dart';
import '../../../core/session/session_controller.dart';
import '../../../core/widgets/comunes.dart';
import '../domain/auth_models.dart';

/// Verificación de identidad (KYC): foto de la cédula + selfie (prueba de vida).
class KycScreen extends ConsumerStatefulWidget {
  const KycScreen({super.key});

  @override
  ConsumerState<KycScreen> createState() => _KycScreenState();
}

class _KycScreenState extends ConsumerState<KycScreen> {
  Uint8List? _cedula;
  Uint8List? _selfie;
  bool _ocupado = false;
  ResultadoKyc? _resultado;

  /// PNG 1×1 usado como imagen de demostración cuando no hay cámara (escritorio/web/emulador).
  static final Uint8List imagenDemo = base64Decode(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==');

  Future<void> _capturar(bool esSelfie, ImageSource fuente) async {
    try {
      final foto = await ImagePicker().pickImage(
        source: fuente,
        preferredCameraDevice: esSelfie ? CameraDevice.front : CameraDevice.rear,
        maxWidth: 1600,
        imageQuality: 80,
      );
      if (foto == null) return;
      final bytes = await foto.readAsBytes();
      setState(() => esSelfie ? _selfie = bytes : _cedula = bytes);
    } catch (_) {
      if (!mounted) return;
      mostrarMensaje(context, 'No pudimos abrir la cámara. Usa la imagen de demostración.');
    }
  }

  void _usarDemo(bool esSelfie) =>
      setState(() => esSelfie ? _selfie = imagenDemo : _cedula = imagenDemo);

  Future<void> _enviar() async {
    setState(() => _ocupado = true);
    try {
      final r = await ref.read(authRepositoryProvider).enviarKyc([
        DocumentoKyc(tipo: 'cedula', contenidoBase64: base64Encode(_cedula!)),
        DocumentoKyc(tipo: 'selfie', contenidoBase64: base64Encode(_selfie!)),
      ]);
      setState(() => _resultado = r);
      if (r.aprobado) _marcarAprobado();
    } catch (e) {
      if (mounted) mostrarError(context, e);
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  void _marcarAprobado() {
    final s = ref.read(sessionProvider);
    if (s != null) {
      ref.read(sessionProvider.notifier).actualizarUsuario(s.usuario.copyWith(kycAprobado: true));
    }
  }

  Future<void> _consultarEstado() async {
    setState(() => _ocupado = true);
    try {
      final p = await ref.read(authRepositoryProvider).perfil();
      if (p.kycAprobado) {
        _marcarAprobado();
      } else if (mounted) {
        mostrarMensaje(context, 'Tu verificación sigue en revisión.');
      }
    } catch (e) {
      if (mounted) mostrarError(context, e);
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final r = _resultado;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Verifica tu identidad'),
        actions: [
          TextButton(
            onPressed: () => ref.read(sessionProvider.notifier).cerrar(),
            child: const Text('Salir'),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Text('Para proteger tu dinero necesitamos confirmar que eres tú.', style: t.titleMedium),
          const SizedBox(height: 6),
          Text(
            'Tus fotos se guardan cifradas y solo se usan para la verificación (KYC). '
            'Sin este paso no podrás vincular bancos, crear metas ni pagar.',
            style: t.bodyMedium,
          ),
          const SizedBox(height: 20),
          _TarjetaFoto(
            titulo: 'Foto de tu cédula',
            icono: Icons.badge_outlined,
            bytes: _cedula,
            alCamara: () => _capturar(false, ImageSource.camera),
            alGaleria: () => _capturar(false, ImageSource.gallery),
            alDemo: () => _usarDemo(false),
          ),
          const SizedBox(height: 12),
          _TarjetaFoto(
            titulo: 'Selfie (prueba de vida)',
            icono: Icons.face_retouching_natural,
            bytes: _selfie,
            alCamara: () => _capturar(true, ImageSource.camera),
            alGaleria: () => _capturar(true, ImageSource.gallery),
            alDemo: () => _usarDemo(true),
          ),
          const SizedBox(height: 20),
          if (r != null) ...[
            _ResultadoKyc(resultado: r),
            const SizedBox(height: 12),
          ],
          if (r?.resultado == 'pendiente')
            OutlinedButton.icon(
              onPressed: _ocupado ? null : _consultarEstado,
              icon: const Icon(Icons.refresh),
              label: const Text('Consultar estado'),
            )
          else
            FilledButton(
              onPressed: _ocupado || _cedula == null || _selfie == null ? null : _enviar,
              child: _ocupado
                  ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2.5))
                  : Text(r?.resultado == 'rechazado' ? 'Intentar de nuevo' : 'Enviar verificación'),
            ),
        ],
      ),
    );
  }
}

class _TarjetaFoto extends StatelessWidget {
  const _TarjetaFoto({
    required this.titulo,
    required this.icono,
    required this.bytes,
    required this.alCamara,
    required this.alGaleria,
    required this.alDemo,
  });

  final String titulo;
  final IconData icono;
  final Uint8List? bytes;
  final VoidCallback alCamara;
  final VoidCallback alGaleria;
  final VoidCallback alDemo;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final lista = bytes != null;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(lista ? Icons.check_circle : icono, color: lista ? cs.primary : cs.onSurfaceVariant),
                const SizedBox(width: 8),
                Expanded(child: Text(titulo, style: Theme.of(context).textTheme.titleSmall)),
                if (lista && bytes!.length > 100)
                  ClipRRect(
                    borderRadius: BorderRadius.circular(8),
                    child: Image.memory(bytes!, width: 48, height: 48, fit: BoxFit.cover),
                  ),
              ],
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 4,
              children: [
                OutlinedButton.icon(onPressed: alCamara, icon: const Icon(Icons.photo_camera_outlined), label: const Text('Cámara')),
                OutlinedButton.icon(onPressed: alGaleria, icon: const Icon(Icons.photo_library_outlined), label: const Text('Galería')),
                TextButton(onPressed: alDemo, child: const Text('Imagen de demostración')),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _ResultadoKyc extends StatelessWidget {
  const _ResultadoKyc({required this.resultado});

  final ResultadoKyc resultado;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final (icono, color, texto) = switch (resultado.resultado) {
      'aprobado' => (Icons.verified, cs.primary, '¡Identidad verificada! Bienvenido a Ahorra+.'),
      'rechazado' => (
          Icons.error_outline,
          cs.error,
          'No pudimos verificarte: ${resultado.motivoRechazo ?? 'intenta con fotos más nítidas'}.'
        ),
      _ => (Icons.hourglass_top, cs.tertiary, 'Tu verificación está en revisión. Te avisaremos.'),
    };
    return Row(
      children: [
        Icon(icono, color: color),
        const SizedBox(width: 8),
        Expanded(child: Text(texto, style: TextStyle(color: color))),
      ],
    );
  }
}
