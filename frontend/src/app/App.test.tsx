import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";

afterEach(() => {
  cleanup();
  sessionStorage.clear();
  vi.unstubAllGlobals();
});

test("loads the signed-in workspace once and displays its data", async () => {
  sessionStorage.setItem("wastecollect.tokens", JSON.stringify({ accessToken: "test-access", refreshToken: "test-refresh" }));
  sessionStorage.setItem("wastecollect.role", "RESIDENT");
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    const payload = url.endsWith("/users/me")
      ? { displayName: "Ahnaf", role: "RESIDENT" }
      : url.endsWith("/zones")
        ? [{ id: "zone-1", name: "Central zone" }]
        : url.endsWith("/waste-categories")
          ? [{ id: "category-1", name: "General waste", allowedUnit: "BAG" }]
          : [];
    return new Response(JSON.stringify(payload), { status: 200, headers: { "Content-Type": "application/json" } });
  });
  vi.stubGlobal("fetch", fetchMock);
  render(<MemoryRouter initialEntries={["/account"]}><App /></MemoryRouter>);

  expect(await screen.findByRole("heading", { name: "Hello, Ahnaf." })).toBeInTheDocument();
  expect(await screen.findByRole("button", { name: "Request pickup" })).toBeEnabled();
  expect(screen.getByRole("option", { name: "Central zone" })).toBeInTheDocument();
  expect(screen.getByText("No pickup requests yet.")).toBeInTheDocument();
  expect(screen.queryByText("Loading your workspace…")).not.toBeInTheDocument();
  expect(fetchMock).toHaveBeenCalledTimes(4);
});

test("renders the WasteCollect foundation shell", () => {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  expect(screen.getByRole("link", { name: "WasteCollect" })).toBeInTheDocument();
  expect(screen.getByRole("heading", { name: /cleaner streets/i })).toBeInTheDocument();
});

test("renders the M2 sign-in form", () => {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={["/login"]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  expect(screen.getByRole("heading", { name: "Sign in" })).toBeInTheDocument();
  expect(screen.getByLabelText("Email address")).toHaveAttribute("type", "email");
  expect(screen.getByLabelText("Password")).toHaveAttribute("type", "password");
});
