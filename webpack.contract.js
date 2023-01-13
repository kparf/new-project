const host =
  (typeof window === "undefined" && process.env.DEV_HOSTNAME) || "localhost"

module.exports = {
  moduleFederationPlugin: {
    name: "reactTemplateAppName",
    file: {
      name: "reactTemplateAppNameManifest",
      extension: "js",
    },
    react: {
      eager: true,
    },
  },
  devServer: {
    host,
    port: 9000,
  },
}
