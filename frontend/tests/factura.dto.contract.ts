import type { FacturaReporteDTO } from '../src/models/factura.dto.js';
import type { EstadoFiscal } from '../src/models/arca.dto.js';

type Igual<A, B> = (<T>() => T extends A ? 1 : 2) extends (<T>() => T extends B ? 1 : 2) ? true : false;
type Exigir<T extends true> = T;

// Los campos serializados siempre existen; los fiscales sin valor llevan null.
type Fiscal = Exigir<Igual<FacturaReporteDTO['estadoFiscal'], EstadoFiscal | null>>;
type Numero = Exigir<Igual<FacturaReporteDTO['numeroFiscal'], number | null>>;
type PuntoFiscal = Exigir<Igual<FacturaReporteDTO['puntoVentaFiscal'], number | null>>;
type PuntoNumero = Exigir<Igual<FacturaReporteDTO['puntoVentaNumero'], number | null>>;
type Tipo = Exigir<Igual<FacturaReporteDTO['tipoComprobanteFiscal'], number | null>>;
type Vencimiento = Exigir<Igual<FacturaReporteDTO['caeVencimiento'], string | null>>;
type Mensaje = Exigir<Igual<FacturaReporteDTO['mensajeArca'], string | null>>;
type Permisos = Exigir<Igual<FacturaReporteDTO['puedeAutorizar' | 'puedeConsultar' | 'puedeModificar' | 'puedeAnular' | 'puedeEliminar'], boolean>>;
type Requeridos = Exigir<Igual<{} extends Pick<FacturaReporteDTO,
    'estadoFiscal' | 'numeroFiscal' | 'puntoVentaFiscal' | 'puntoVentaNumero' | 'tipoComprobanteFiscal'
    | 'caeVencimiento' | 'mensajeArca' | 'puedeAutorizar' | 'puedeConsultar' | 'puedeModificar' | 'puedeAnular' | 'puedeEliminar'> ? true : false, false>>;
