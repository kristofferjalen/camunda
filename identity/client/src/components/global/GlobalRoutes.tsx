/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Route, Routes as RouterRoutes } from "react-router-dom";
import Redirect from "src/components/global/Redirect";
import { useGlobalRoutes } from "src/components/global/useGlobalRoutes";

const GlobalRoutes: FC = () => {
  const routes = useGlobalRoutes();
  const indexRoute = routes[0].key;

  return (
    <RouterRoutes>
      <Route index element={<Redirect to={indexRoute} />} />
      {routes.map(({ path, element }) => {
        return <Route key={path} path={path} element={element} />;
      })}
      <Route path="*" element={<Redirect to="/" />} />
    </RouterRoutes>
  );
};

export default GlobalRoutes;
