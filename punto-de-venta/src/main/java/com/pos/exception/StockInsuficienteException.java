package com.pos.exception;

public class StockInsuficienteException extends PosException {

    private final int stockDisponible;
    private final int cantidadSolicitada;

    public StockInsuficienteException(int stockDisponible, int cantidadSolicitada) {
        super("Stock insuficiente. Disponible: " + stockDisponible + ", solicitado: " + cantidadSolicitada);
        this.stockDisponible = stockDisponible;
        this.cantidadSolicitada = cantidadSolicitada;
    }

    public int getStockDisponible() {
        return stockDisponible;
    }

    public int getCantidadSolicitada() {
        return cantidadSolicitada;
    }
}
