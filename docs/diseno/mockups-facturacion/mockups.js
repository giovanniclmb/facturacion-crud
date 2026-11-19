const vistas = {
    listado: 'listado',
    alta: 'formulario',
    consumidor: 'formulario',
    edicion: 'formulario',
    articulos: 'articulos',
    pdf: 'comprobante',
    interno: 'comprobante',
};
const parametros = new URLSearchParams(location.search);
const solicitada = parametros.get('vista');
const vista = Object.hasOwn(vistas, solicitada) ? solicitada : 'listado';

for (const pantalla of document.querySelectorAll('[data-vista]')) {
    pantalla.hidden = pantalla.dataset.vista !== vistas[vista];
}
document.querySelector(`nav a[href="?vista=${vista}"]`).setAttribute('aria-current', 'page');

const elemento = id => document.getElementById(id);
const tipo = elemento('tipo');
tipo.value = vista === 'consumidor' ? 'consumidor' : 'alta';
tipo.addEventListener('change', () => { location.search = 'vista=' + tipo.value; });

if (vista === 'consumidor') {
    elemento('cliente').hidden = true;
    elemento('punto').selectedIndex = 1;
    elemento('guardar').textContent = 'Guardar comprobante interno';
    elemento('nota-formulario').textContent = 'El comprobante interno se guarda sin solicitar CAE.';
}
if (vista === 'edicion') {
    elemento('titulo-formulario').textContent = 'Editar Factura C · N.º local 36';
    elemento('punto').disabled = true;
    tipo.disabled = true;
    elemento('rechazo').hidden = false;
    elemento('nota-formulario').textContent = 'Se actualizarán los precios y se volverá a solicitar el CAE.';
}
const lista = elemento('lista');
function mostrarPrecios() {
    const mayorista = lista.selectedIndex === 1;
    elemento('lista-a').textContent = mayorista ? 'Mayorista' : 'General';
    elemento('precio-a').textContent = mayorista ? '$ 10.500,00' : '$ 12.500,00';
    elemento('alternativa').hidden = !mayorista;
}
lista.addEventListener('change', mostrarPrecios);
if (parametros.get('lista') === 'mayorista') {
    lista.selectedIndex = 1;
    mostrarPrecios();
}

if (vista === 'interno') {
    elemento('tipo-pdf').textContent = 'Comprobante interno';
    elemento('numero-pdf').textContent = 'N.º 34';
    elemento('leyenda-pdf').textContent = 'Comprobante interno · sin validez fiscal';
    elemento('receptor-pdf').textContent = 'Consumidor final';
    elemento('datos-fiscales').hidden = true;
}
elemento('imprimir').addEventListener('click', () => window.print());
