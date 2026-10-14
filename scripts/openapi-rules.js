module.exports = {
  id: 'slot-reservation-rules',
  rules: {
    oas3: {
      'api-v1-path-prefix': () => ({
        Paths: {
          enter(paths, { location, report }) {
            for (const path of Object.keys(paths)) {
              if (!path.startsWith('/api/v1/')) {
                report({
                  message: 'Las rutas deben comenzar con /api/v1/.',
                  location: location.child(path).key(),
                });
              }
            }
          },
        },
      }),
    },
  },
};
