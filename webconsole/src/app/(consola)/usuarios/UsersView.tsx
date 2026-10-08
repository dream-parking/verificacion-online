"use client";

import { useCallback, useState, type CSSProperties } from "react";
import { useConsola } from "@/components/consola/ConsolaProvider";
import {
  Cargando,
  Encabezado,
  ErrorCarga,
  FiltroSelect,
  FiltroTexto,
  mensajeDeError,
  Paginacion,
  PanelFiltros,
  useCarga,
  useFiltrosPaginados,
  Vacio,
} from "@/components/consola/ui";
import { apiFetch, type Rol, type UsuarioConsola } from "@/lib/consola/api";
import { ROL } from "@/lib/consola/formato";
import { PasswordDialog } from "./PasswordDialog";

const INITIAL_FILTERS = { text: "", role: "todos", status: "todos" };

function matches(u: UsuarioConsola, f: typeof INITIAL_FILTERS) {
  const text = f.text.trim().toLowerCase();
  if (text && !`${u.fullName} ${u.email}`.toLowerCase().includes(text)) return false;
  if (f.role !== "todos" && u.role !== f.role) return false;
  if (f.status === "activos" && !u.active) return false;
  if (f.status === "inactivos" && u.active) return false;
  return true;
}

type Notice = { text: string };

export function UsersView() {
  const { usuario, token } = useConsola();
  const isAdmin = usuario.role === "ADMIN";
  // Solo un administrador puede pedir la lista completa (con inactivos) y definir contraseñas.
  const users = useCarga(isAdmin ? "users" : null, (t) =>
    apiFetch<UsuarioConsola[]>("/api/console/users?includeInactive=true", { token: t }),
  );
  const { filtros: f, setFiltro, limpiar, hayFiltros, tamano, setTamano, setPagina, paginar } =
    useFiltrosPaginados(INITIAL_FILTERS);
  const [target, setTarget] = useState<UsuarioConsola | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);

  const closeDialog = useCallback(() => setTarget(null), []);

  if (!isAdmin) {
    return (
      <div className="flex max-w-[860px] min-w-0 flex-col gap-5">
        <Encabezado titulo="Usuarios">Gestión de las personas que entran a la consola.</Encabezado>
        <div className="card" role="alert">
          <div className="font-display text-xl font-extrabold">Solo para administradores</div>
          <p className="mt-2 mb-0 max-w-[520px] text-[15px] leading-[22px] text-muted">
            Tu rol ({ROL[usuario.role]}) no puede ver ni gestionar usuarios. Si necesitas una contraseña nueva, pídesela
            a un administrador.
          </p>
        </div>
      </div>
    );
  }

  const list = users.datos ?? [];
  const filtered = list.filter((u) => matches(u, f));
  const pag = paginar(filtered);
  const roles = Object.keys(ROL) as Rol[];

  return (
    <div className="flex min-w-0 flex-col gap-5">
      <Encabezado titulo="Usuarios">
        {users.datos ? `${filtered.length} de ${list.length} usuarios. ` : ""}Define la contraseña de cada persona para que
        pueda entrar a la consola.
      </Encabezado>

      <PanelFiltros etiqueta="usuarios" hayFiltros={hayFiltros} onLimpiar={limpiar}>
        <FiltroTexto
          id="u-q"
          label="Nombre o correo"
          placeholder="Por ejemplo, Ana"
          value={f.text}
          onChange={(v) => setFiltro("text", v)}
        />
        <FiltroSelect
          id="u-r"
          label="Rol"
          value={f.role}
          onChange={(v) => setFiltro("role", v)}
          opciones={[{ value: "todos", label: "Todos" }, ...roles.map((r) => ({ value: r, label: ROL[r] }))]}
        />
        <FiltroSelect
          id="u-e"
          label="Estado"
          value={f.status}
          onChange={(v) => setFiltro("status", v)}
          opciones={[
            { value: "todos", label: "Todos" },
            { value: "activos", label: "Activos" },
            { value: "inactivos", label: "Inactivos" },
          ]}
        />
      </PanelFiltros>

      {notice && (
        <div
          key={notice.text}
          role="status"
          className="anim-aviso flex items-start justify-between gap-3 border border-[#0b6b4a] bg-[#e2f5ec] px-4 py-3 text-[15px] leading-[22px] font-semibold text-[#0b4f37]"
        >
          <span className="[overflow-wrap:anywhere]">✓ {notice.text}</span>
          <button
            type="button"
            className="-my-1 flex-none rounded-full px-2 text-lg leading-none hover:bg-black/5"
            aria-label="Cerrar aviso"
            onClick={() => setNotice(null)}
          >
            ×
          </button>
        </div>
      )}

      <div className="tblwrap" role="table" aria-label="Usuarios de la consola">
        <div className="gh g-usr" role="row">
          <div role="columnheader">Nombre</div>
          <div role="columnheader">Correo</div>
          <div role="columnheader">Cargo</div>
          <div role="columnheader">Rol</div>
          <div role="columnheader">Estado</div>
          <div role="columnheader">Contraseña</div>
        </div>

        {users.cargando && (
          <Cargando
            etiqueta="usuarios"
            className="md:min-w-[1100px]"
            grid="g-usr"
            anchos={["70%", "80%", "70%", "110px", "70px", "120px"]}
          />
        )}
        {users.error && (
          <ErrorCarga
            titulo="No pudimos cargar los usuarios"
            texto={mensajeDeError(users.error, "Hubo un problema con el servidor. Intenta de nuevo en un momento.")}
            onRetry={users.recargar}
          />
        )}
        {users.datos && list.length === 0 && (
          <Vacio titulo="Todavía no hay usuarios" texto="Los usuarios de la consola aparecerán aquí." />
        )}
        {list.length > 0 && filtered.length === 0 && (
          <Vacio
            titulo="No encontramos usuarios"
            texto="Prueba con otros filtros, o quítalos para ver todos."
            onLimpiar={hayFiltros ? limpiar : undefined}
          />
        )}
        {pag.visibles.map((u, i) => (
          <div key={u.id} className="gr g-usr anim-fila" style={{ "--i": i } as CSSProperties} role="row">
            <div role="cell" className="font-semibold">
              <span className="clip2" title={u.fullName}>
                {u.fullName}
              </span>
            </div>
            <div role="cell" data-label="Correo">
              <span className="clip2" title={u.email}>
                {u.email}
              </span>
            </div>
            <div role="cell" data-label="Cargo">
              <span className="clip2" title={u.jobTitle ?? undefined}>
                {u.jobTitle || "—"}
              </span>
            </div>
            <div role="cell" data-label="Rol">
              {ROL[u.role]}
            </div>
            <div role="cell" data-label="Estado">
              <span className={`badge ${u.active ? "s-ok" : "s-aband"}`}>{u.active ? "✓ Activo" : "✕ Inactivo"}</span>
            </div>
            <div role="cell" data-label="Contraseña">
              <button
                type="button"
                className="btn2 min-h-9 px-4 text-[12px]"
                aria-label={`Definir contraseña de ${u.fullName}`}
                onClick={() => {
                  setNotice(null);
                  setTarget(u);
                }}
              >
                Definir
              </button>
            </div>
          </div>
        ))}
      </div>

      {filtered.length > 0 && (
        <Paginacion
          etiqueta="usuarios"
          total={pag.total}
          pagina={pag.pagina}
          totalPaginas={pag.totalPaginas}
          tamano={tamano}
          onPagina={setPagina}
          onTamano={setTamano}
        />
      )}

      {target && (
        <PasswordDialog
          user={target}
          token={token}
          onClose={closeDialog}
          onDone={() => {
            setNotice({
              text: `Contraseña definida para ${target.fullName}. Pásasela por un medio seguro: no se puede volver a ver.`,
            });
            setTarget(null);
          }}
        />
      )}
    </div>
  );
}
