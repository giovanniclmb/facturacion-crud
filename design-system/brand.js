const nombre = 'Facturación de ventas';

document.querySelectorAll('[data-brand-name]').forEach(elemento => {
    elemento.textContent = nombre;
});
