/**
 * @license
 * Copyright Akveo. All Rights Reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
// The file contents for the current environment will overwrite these during build.
// The build system defaults to the dev environment which uses `environment.ts`, but if you do
// `ng build --env=prod` then `environment.prod.ts` will be used instead.
// The list of which env maps to which file can be found in `.angular-cli.json`.

export const environment = {
  production: false,
  /*searchUrl: 'http://localhost:7081/api/images/search?imageName=',
  autocompleteUrl: 'http://localhost:7081/api/images/autocomplete?imageName=',
  detailUrl: 'http://localhost:7081/api/images/details?imageName=',
  ingestUrl: 'http://localhost:8080/api/images/request'*/
  searchUrl: 'https://search.c14r.io/api/images/search?imageName=',
  detailUrl: 'https://search.c14r.io/api/images/details?imageName=',
  autocompleteUrl: 'https://search.c14r.io/api/images/autocomplete?imageName=',
  ingestUrl: 'https://ingest.c14r.io/api/images/request'
};
