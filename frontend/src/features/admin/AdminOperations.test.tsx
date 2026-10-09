import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import App from "../../app/App";

const group = { id: "group-1", publicCode: "GRP-TEST", status: "DRAFT", zoneName: "Central Zone", preferredDate: "2026-11-10", memberCount: 1 };
const collector = { id: "collector-1", displayName: "Test Collector", email: "collector@example.com", status: "ACTIVE", activeAssignments: 0 };
const page = { items: [group], total: 1, page: 0, size: 20 };
const dashboard = { requests: [{ status: "PENDING", total: 2 }], groups: [{ status: "DRAFT", total: 1 }], activeCollectors: 1, unassignedGroups: 0 };
function response(payload: unknown, status = 200) {
  return new Response(status === 204 ? null : JSON.stringify(payload), { status, headers: { "Content-Type": "application/json" } });
}
function stubApi(overrides?: (url: URL, options?: RequestInit) => Response | undefined) {
  const mock = vi.fn(async (input: RequestInfo | URL, options?: RequestInit) => {
    const url = new URL(String(input));
    const override = overrides?.(url, options);
    if (override) return override;
    if (url.pathname.endsWith("/dashboard")) return response(dashboard);
    if (url.pathname.endsWith("/settings")) return response({ maxGroupRequests: 50, minimumNoticeHours: 0, serviceTimezone: "Asia/Dhaka" });
    if (url.pathname.endsWith("/audit")) return response({ ...page, items: [] });
    if (url.pathname.endsWith("/collectors")) return response([collector]);
    if (url.pathname.endsWith("/zones")) return response([{ id: "zone-1", name: "Central Zone" }]);
    if (url.pathname.endsWith("/assignments")) return response([]);
    return response(page);
  });
  vi.stubGlobal("fetch", mock);
  return mock;
}
function open(path: string) { render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>); }
beforeEach(() => {
  sessionStorage.setItem("wastecollect.tokens", JSON.stringify({ accessToken: "admin-token", refreshToken: "refresh" }));
  sessionStorage.setItem("wastecollect.role", "ADMIN");
});
afterEach(() => { cleanup(); sessionStorage.clear(); vi.unstubAllGlobals(); });

test.each([
  ["/admin", "Operations overview", "Staffing"],
  ["/admin/requests", "Pickup requests", "GRP-TEST"],
  ["/admin/scheduling", "Collection scheduling", "GRP-TEST"],
  ["/admin/collectors", "Collector management", "Test Collector"],
  ["/admin/audit", "Audit history", "No operational changes recorded."],
  ["/admin/settings", "Operational settings", "Save settings"],
])("loads administrator page %s", async (path, title, content) => {
  const mock = stubApi(); open(path);
  expect(screen.getByRole("heading", { name: title })).toBeInTheDocument();
  expect(await screen.findByText(content)).toBeInTheDocument();
  expect(screen.queryByText("Loading operations…")).not.toBeInTheDocument();
  expect(screen.getByRole("navigation", { name: "Administrator navigation" })).toBeInTheDocument();
  expect(mock.mock.calls.filter(([url]) => String(url).includes("/admin/")).every(([, options]) => (options?.headers as Record<string,string>).Authorization === "Bearer admin-token")).toBe(true);
});

test("saves a collection window and loads its updated scheduled state", async () => {
  let scheduled = false;
  const mock = stubApi((url) => {
    if (url.pathname.endsWith("/schedule")) { scheduled = true; return response(null, 204); }
    if (url.pathname.endsWith("/collections")) return response({ ...page, items: [{ ...group, status: scheduled ? "SCHEDULED" : "DRAFT" }] });
  });
  open("/admin/scheduling");
  fireEvent.click(await screen.findByRole("button", { name: "Manage" }));
  const form = screen.getByRole("heading", { name: "Schedule" }).closest("form")!;
  fireEvent.change(within(form).getByLabelText("Start"), { target: { value: "2026-11-10T09:00" } });
  fireEvent.change(within(form).getByLabelText("End"), { target: { value: "2026-11-10T10:00" } });
  fireEvent.change(within(form).getByLabelText("Reason"), { target: { value: "Morning collection" } });
  fireEvent.submit(form);
  expect(await screen.findByText("Collection schedule saved.")).toBeInTheDocument();
  expect(await screen.findByText("SCHEDULED", { selector: "span.status" })).toBeInTheDocument();
  const call = mock.mock.calls.find(([url]) => String(url).endsWith("/groups/group-1/schedule"))!;
  expect(call[1]?.method).toBe("PATCH");
  expect(JSON.parse(String(call[1]?.body))).toEqual({ startsAt: new Date("2026-11-10T09:00").toISOString(), endsAt: new Date("2026-11-10T10:00").toISOString(), reason: "Morning collection" });
});

test("shows assignment conflicts without losing the editable group", async () => {
  stubApi((url) => {
    if (url.pathname.endsWith("/collections")) return response({ ...page, items: [{ ...group, status: "SCHEDULED" }] });
    if (url.pathname.endsWith("/assignment")) return response({ message: "Collector already has an overlapping assignment" }, 409);
  });
  open("/admin/scheduling");
  fireEvent.click(await screen.findByRole("button", { name: "Manage" }));
  const form = screen.getByRole("heading", { name: "Assign collector" }).closest("form")!;
  fireEvent.change(within(form).getByLabelText("Collector"), { target: { value: "collector-1" } });
  fireEvent.change(within(form).getByLabelText("Reason"), { target: { value: "Cover this route" } });
  fireEvent.submit(form);
  expect(await screen.findByRole("alert")).toHaveTextContent("Collector already has an overlapping assignment");
  expect(screen.getByRole("heading", { name: "Manage GRP-TEST" })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Save assignment" })).toBeEnabled();
});

test("request search sends filters and advances server pagination", async () => {
  const mock = stubApi((url) => url.pathname.endsWith("/requests") ? response({ ...page, total: 21 }) : undefined);
  open("/admin/requests");
  await screen.findByText("GRP-TEST");
  fireEvent.change(screen.getByLabelText("Search"), { target: { value: "Central" } });
  fireEvent.click(screen.getByRole("button", { name: "Search" }));
  await waitFor(() => expect(mock.mock.calls.some(([url]) => new URL(String(url)).searchParams.get("query") === "Central")).toBe(true));
  await screen.findByText("GRP-TEST");
  fireEvent.click(screen.getByRole("button", { name: "Next" }));
  await waitFor(() => expect(mock.mock.calls.some(([url]) => new URL(String(url)).searchParams.get("page") === "1")).toBe(true));
  expect(await screen.findByText("Page 2 · 21 records")).toBeInTheDocument();
});

test("saves operational settings as numeric values", async () => {
  const mock = stubApi((url, options) => url.pathname.endsWith("/settings") && options?.method === "PATCH" ? response({ maxGroupRequests: 25, minimumNoticeHours: 2, serviceTimezone: "Asia/Dhaka" }) : undefined);
  open("/admin/settings");
  await screen.findByRole("button", { name: "Save settings" });
  fireEvent.change(screen.getByLabelText("Maximum requests per group"), { target: { value: "25" } });
  fireEvent.change(screen.getByLabelText("Minimum scheduling notice (hours)"), { target: { value: "2" } });
  fireEvent.submit(screen.getByRole("button", { name: "Save settings" }).closest("form")!);
  expect(await screen.findByText("Settings saved.")).toBeInTheDocument();
  expect(JSON.parse(String(mock.mock.calls.find(([, options]) => options?.method === "PATCH")?.[1]?.body))).toEqual({ maxGroupRequests: 25, minimumNoticeHours: 2 });
});

test("an operations API error clears the spinner and can be retried", async () => {
  let failed = false;
  stubApi(url => {
    if (url.pathname.endsWith("/dashboard") && !failed) { failed = true; return response({ message: "Temporarily unavailable" }, 503); }
  });
  open("/admin");
  expect(await screen.findByRole("alert")).toHaveTextContent("Temporarily unavailable");
  expect(screen.queryByText("Loading operations…")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Retry" }));
  expect(await screen.findByText("Staffing")).toBeInTheDocument();
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
});

test("an administrator can inspect a request and its status history", async () => {
  stubApi(url => url.pathname.endsWith("/requests/group-1") ? response({ ...group, residentName: "Test Resident", categoryName: "General waste", quantity: 2, unit: "BAG", address: "12 Test Road", notes: "Side gate", history: [{ nextStatus: "GROUPED", reason: "Confirmed into group", createdAt: "2026-10-09T09:00:00Z", actorName: "Test Admin" }] }) : undefined);
  open("/admin/requests");
  fireEvent.click(await screen.findByRole("button", { name: "View details" }));
  expect(await screen.findByText("12 Test Road")).toBeInTheDocument();
  expect(screen.getByText("Confirmed into group")).toBeInTheDocument();
  expect(screen.getByText("Side gate")).toBeInTheDocument();
});
