import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { expect, test } from "vitest";
import App from "./App";

test("renders the WasteCollect foundation shell", () => {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  expect(screen.getByRole("link", { name: "WasteCollect" })).toBeInTheDocument();
  expect(screen.getByRole("heading", { name: /cleaner collections/i })).toBeInTheDocument();
});
