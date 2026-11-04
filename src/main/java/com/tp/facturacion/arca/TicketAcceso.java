package com.tp.facturacion.arca;
import java.time.Clock;
import java.time.Instant;
public final class TicketAcceso {
    private final String token;
    private final String sign;
    private final Instant expiracion;
    public TicketAcceso(String token,String sign,Instant expiracion) {
        if(token==null || token.isBlank() || sign==null || sign.isBlank() || expiracion==null) throw new ArcaRemotaException();
        this.token=token;this.sign=sign;this.expiracion=expiracion;
    }
    public String token() {return token;}
    public String sign() {return sign;}
    public Instant expiracion() {return expiracion;}
    public boolean vigente(Clock reloj) {return expiracion.isAfter(reloj.instant().plusSeconds(600));}
    @Override public String toString() {return "TicketAcceso[credenciales redactadas]";}
}
