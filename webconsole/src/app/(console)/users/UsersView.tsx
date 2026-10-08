"use client";

import { useCallback, useState, type CSSProperties } from "react";
import { useConsole } from "@/components/console/ConsoleProvider";
import {
  Loading,
  PageHeading,
  LoadError,
  SelectFilter,
  TextFilter,
  Pagination,
  FiltersPanel,
  Empty,
} from "@/components/console/Ui";
import { errorMessage, useLoad, usePaginatedFilters } from "@/lib/console/hooks";
import { apiFetch, type Role, type ConsoleUser } from "@/lib/console/api";
import { ROLE_LABEL } from "@/lib/console/format";
import { PasswordDialog } from "./PasswordDialog";

const INITIAL_FILTERS = { text: "", role: "all", status: "all" };

function matches(u: ConsoleUser, f: typeof INITIAL_FILTERS) {
  const text = f.text.trim().toLowerCase();
  if (text && !`${u.fullName} ${u.email}`.toLowerCase().includes(text)) return false;
  if (f.role !== "all" && u.role !== f.role) return false;
  if (f.status === "active" && !u.active) return false;
  if (f.status === "inactive" && u.active) return false;
  return true;
}

type Notice = { text: string };

export function UsersView() {
  const { user, token } = useConsole();
  const isAdmin = user.role === "ADMIN";
  // Only an administrator can request the full list (with inactive users) and set passwords.
  const users = useLoad(isAdmin ? "users" : null, (t) =>
    apiFetch<ConsoleUser[]>("/api/console/users?includeInactive=true", { token: t }),
  );
  const { filters: f, setFilter, clear, hasFilters, size, setSize, setPage, paginate } =
    usePaginatedFilters(INITIAL_FILTERS);
  const [target, setTarget] = useState<ConsoleUser | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);

  const closeDialog = useCallback(() => setTarget(null), []);

  if (!isAdmin) {
    return (
      <div className="flex max-w-[860px] min-w-0 flex-col gap-5">
        <PageHeading title="Usuarios">Gestión de las personas que entran a la consola.</PageHeading>
        <div className="card" role="alert">
          <div className="font-display text-xl font-extrabold">Solo para administradores</div>
          <p className="mt-2 mb-0 max-w-[520px] text-[15px] leading-[22px] text-muted">
            Tu rol ({ROLE_LABEL[user.role]}) no puede ver ni gestionar usuarios. Si necesitas una contraseña nueva, pídesela
            a un administrador.
          </p>
        </div>
      </div>
    );
  }

  const list = users.data ?? [];
  const filtered = list.filter((u) => matches(u, f));
  const pagination = paginate(filtered);
  const roles = Object.keys(ROLE_LABEL) as Role[];

  return (
    <div className="flex min-w-0 flex-col gap-5">
      <PageHeading title="Usuarios">
        {users.data ? `${filtered.length} de ${list.length} usuarios. ` : ""}Define la contraseña de cada persona para que
        pueda entrar a la consola.
      </PageHeading>

      <FiltersPanel label="usuarios" hasFilters={hasFilters} onClear={clear}>
        <TextFilter
          id="u-q"
          label="Nombre o correo"
          placeholder="Por ejemplo, Ana"
          value={f.text}
          onChange={(v) => setFilter("text", v)}
        />
        <SelectFilter
          id="u-r"
          label="Rol"
          value={f.role}
          onChange={(v) => setFilter("role", v)}
          options={[{ value: "all", label: "Todos" }, ...roles.map((r) => ({ value: r, label: ROLE_LABEL[r] }))]}
        />
        <SelectFilter
          id="u-e"
          label="Estado"
          value={f.status}
          onChange={(v) => setFilter("status", v)}
          options={[
            { value: "all", label: "Todos" },
            { value: "active", label: "Activos" },
            { value: "inactive", label: "Inactivos" },
          ]}
        />
      </FiltersPanel>

      {notice && (
        <div
          key={notice.text}
          role="status"
          className="anim-notice flex items-start justify-between gap-3 border border-[#0b6b4a] bg-[#e2f5ec] px-4 py-3 text-[15px] leading-[22px] font-semibold text-[#0b4f37]"
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
        <div className="gh g-users" role="row">
          <div role="columnheader">Nombre</div>
          <div role="columnheader">Correo</div>
          <div role="columnheader">Cargo</div>
          <div role="columnheader">Rol</div>
          <div role="columnheader">Estado</div>
          <div role="columnheader">Contraseña</div>
        </div>

        {users.loading && (
          <Loading
            label="usuarios"
            className="md:min-w-[1100px]"
            grid="g-users"
            widths={["70%", "80%", "70%", "110px", "70px", "120px"]}
          />
        )}
        {users.error && (
          <LoadError
            title="No pudimos cargar los usuarios"
            text={errorMessage(users.error, "Hubo un problema con el servidor. Intenta de nuevo en un momento.")}
            onRetry={users.reload}
          />
        )}
        {users.data && list.length === 0 && (
          <Empty title="Todavía no hay usuarios" text="Los usuarios de la consola aparecerán aquí." />
        )}
        {list.length > 0 && filtered.length === 0 && (
          <Empty
            title="No encontramos usuarios"
            text="Prueba con otros filtros, o quítalos para ver todos."
            onClear={hasFilters ? clear : undefined}
          />
        )}
        {pagination.visibleItems.map((u, i) => (
          <div key={u.id} className="gr g-users anim-row" style={{ "--i": i } as CSSProperties} role="row">
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
              {ROLE_LABEL[u.role]}
            </div>
            <div role="cell" data-label="Estado">
              <span className={`badge ${u.active ? "s-ok" : "s-abandoned"}`}>{u.active ? "✓ Activo" : "✕ Inactivo"}</span>
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
        <Pagination
          label="usuarios"
          total={pagination.total}
          page={pagination.page}
          totalPages={pagination.totalPages}
          size={size}
          onPage={setPage}
          onSize={setSize}
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
