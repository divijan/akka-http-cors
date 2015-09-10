How to test server-side CORS support:
  1. Make sure you have an http server installed
  2. Put test.html where the server could serve it (from origin different from localhost:8080, see https://developer.mozilla.org/en-US/docs/Web/Security/Same-origin_policy for details)
  3. Run the project
  4. Request test.html with the browser from your server and see what happens. Test is successful if all requests resulted in Success
