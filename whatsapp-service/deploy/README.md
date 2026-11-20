# Despliegue en AWS Lightsail

El servicio necesita un proceso siempre encendido y un disco que guarde la sesión (`auth_info/`). Render gratis no
sirve: el disco se borra en cada despliegue y las 750 horas gratis ya las usa el backend. Corre en una instancia de
**Lightsail `nano_3_0`** (USD 5/mes, 512 MB, 20 GB, IPv4 pública): solo hace conexiones de salida, a WhatsApp y al
backend de Render, así que no necesita ningún puerto abierto salvo SSH.

| Pieza | Dónde |
| --- | --- |
| Código | `/opt/cotizaia` (clon de este repo, rama `main`) |
| Secretos | `/opt/cotizaia/whatsapp-service/.env` y `auth_info/` (permisos solo para el usuario `cotizaia`) |
| Proceso | `cotizaia-whatsapp.service` (systemd), reinicia solo y arranca con la máquina |

## Montarlo

1. Vincular el número en un PC con `npm run iniciar` (crea `.env` y `auth_info/`) y **cerrar** esa ventana.
2. Crear la instancia (Debian 12) y pasar `deploy/setup.sh` como launch script, o correrlo con `sudo bash`.
3. Copiar los secretos y arrancar:

   ```bash
   tar czf - .env auth_info | ssh admin@IP 'sudo tar xzf - -C /opt/cotizaia/whatsapp-service &&
     sudo chown -R cotizaia:cotizaia /opt/cotizaia/whatsapp-service && sudo chmod 600 /opt/cotizaia/whatsapp-service/.env &&
     sudo systemctl start cotizaia-whatsapp'
   ```

Nunca dejar el PC y el servidor con la misma sesión encendidos a la vez: WhatsApp cierra una de las dos.

## Operar

```bash
sudo journalctl -u cotizaia-whatsapp -f      # logs
sudo systemctl restart cotizaia-whatsapp
sudo bash /opt/cotizaia/whatsapp-service/deploy/setup.sh   # actualizar a la última versión de main
```

Si WhatsApp cierra la sesión, el servicio se detiene tras 5 intentos. Vincular de nuevo en un PC, copiar otra vez
`auth_info/` y `sudo systemctl reset-failed cotizaia-whatsapp && sudo systemctl start cotizaia-whatsapp`.
