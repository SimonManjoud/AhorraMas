import 'dart:convert';

import '../../../core/storage/almacen_seguro.dart';

/// Lo que este equipo recuerda del último usuario que ingresó: nombre y bancos vinculados,
/// nunca montos. Permite mostrar "tus bancos" en el login sin exponer una consulta pública
/// por cédula: solo aparece en el teléfono donde la persona ya entró antes.
class RecuerdoEquipo {
  const RecuerdoEquipo({
    required this.nacionalidad,
    required this.cedula,
    required this.primerNombre,
    required this.bancos,
  });

  final String nacionalidad;
  final int cedula;
  final String primerNombre;
  final List<BancoRecordado> bancos;

  bool esDe(String nac, int ced) => nacionalidad == nac && cedula == ced;

  Map<String, Object> aJson() => {
        'nacionalidad': nacionalidad,
        'cedula': cedula,
        'primerNombre': primerNombre,
        'bancos': [for (final b in bancos) b.aJson()],
      };

  static RecuerdoEquipo? desdeJson(String? raw) {
    if (raw == null) return null;
    try {
      final j = (jsonDecode(raw) as Map).cast<String, dynamic>();
      return RecuerdoEquipo(
        nacionalidad: j['nacionalidad'] as String? ?? 'V',
        cedula: (j['cedula'] as num).toInt(),
        primerNombre: j['primerNombre'] as String? ?? '',
        bancos: [
          for (final b in (j['bancos'] as List?) ?? const [])
            BancoRecordado.desdeJson((b as Map).cast<String, dynamic>()),
        ],
      );
    } catch (_) {
      return null; // Un recuerdo corrupto se ignora: solo es una comodidad.
    }
  }
}

class BancoRecordado {
  const BancoRecordado({required this.nombre, required this.tipo});

  final String nombre;

  /// `banco|plataforma_cripto`
  final String tipo;

  bool get esCripto => tipo == 'plataforma_cripto';

  Map<String, Object> aJson() => {'nombre': nombre, 'tipo': tipo};

  static BancoRecordado desdeJson(Map<String, dynamic> j) =>
      BancoRecordado(nombre: j['nombre'] as String? ?? '', tipo: j['tipo'] as String? ?? 'banco');
}

/// Lee y escribe el [RecuerdoEquipo] en el almacén seguro.
class RecuerdoEquipoService {
  RecuerdoEquipoService(this._almacen);

  final AlmacenSeguro _almacen;

  Future<RecuerdoEquipo?> leer() async => RecuerdoEquipo.desdeJson(await _almacen.leer(ClavesAlmacen.recuerdoEquipo));

  /// Tras un login exitoso. Si es otra persona, olvida los bancos del usuario anterior.
  Future<void> recordarUsuario({required String nacionalidad, required int cedula, required String primerNombre}) async {
    final previo = await leer();
    final bancos = previo != null && previo.esDe(nacionalidad, cedula) ? previo.bancos : const <BancoRecordado>[];
    await _guardar(RecuerdoEquipo(nacionalidad: nacionalidad, cedula: cedula, primerNombre: primerNombre, bancos: bancos));
  }

  /// Cuando carga la posición global: guarda solo nombre y tipo de cada institución.
  Future<void> recordarBancos(List<BancoRecordado> bancos) async {
    final previo = await leer();
    if (previo == null) return;
    final iguales = previo.bancos.length == bancos.length &&
        [for (var i = 0; i < bancos.length; i++) previo.bancos[i].nombre == bancos[i].nombre].every((x) => x);
    if (iguales) return;
    await _guardar(RecuerdoEquipo(
      nacionalidad: previo.nacionalidad,
      cedula: previo.cedula,
      primerNombre: previo.primerNombre,
      bancos: bancos,
    ));
  }

  Future<void> olvidar() => _almacen.borrar(ClavesAlmacen.recuerdoEquipo);

  Future<void> _guardar(RecuerdoEquipo r) => _almacen.escribir(ClavesAlmacen.recuerdoEquipo, jsonEncode(r.aJson()));
}
