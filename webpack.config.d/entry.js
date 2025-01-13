+function () {
    const webpack = require('webpack');
    const ContextReplacementPlugin = webpack.ContextReplacementPlugin;
    const path = require("path");

    config.entry = path.resolve(__dirname, "kotlin/BeatMaps-shared.js");
    config.optimization = {
        usedExports: true,
        splitChunks: {
            chunks: "all",
            filename: "[name].js",
            cacheGroups: {
                defaultVendors: {
                    reuseExistingChunk: true
                },
                modules: {
                    test: /kotlinx|node_modules[\\/](react-dom|react[\\/]cjs|react-router|@remix|kotlin-stdlib|@js-joda|moment|axios)/,
                    name: "modules",
                    priority: 30,
                    filename: "modules.js"
                }
            }
            /*name: "modules",
            filename: "[name].[hash:8].js",
            cacheGroups: {
               ,
                kotlin: {
                    test: /kotlin/,
                    name: "kotlin",
                    priority: 20,
                    filename: "kotlin.js"
                },
                dates: {
                    test: /react-with-styles|react-dates/,
                    priority: 50,
                    name: "dates",
                    filename: "dates.js"
                }
            }*/
        }
    };
    config.plugins.push(new ContextReplacementPlugin(/moment[\/\\]locale$/, /en\-gb/));

    const BundleAnalyzerPlugin = require('webpack-bundle-analyzer').BundleAnalyzerPlugin;
    config.plugins.push(new BundleAnalyzerPlugin({
        analyzerMode: 'static',
        reportFilename: '../../../../reports/webpack/BeatMaps/BeatMaps.js.report.html',
        generateStatsFile: true,
        statsFilename: '../../../../reports/webpack/BeatMaps/BeatMaps.js.stats.json',
        openAnalyzer: false
    }));

    config.module.rules.push({
        test: /\.[jt]sx?$/,
        use: {
            loader: "magic-comments-loader",
            options: {
                webpackChunkName: (modulePath, importPath) => {
                    if (/react-with-styles|react-dates/.test(importPath)) {
                        return "dates"
                    } else if (/react-beautiful-dnd/.test(importPath)) {
                        return "dnd"
                    } else if (/admin/.test(importPath)) {
                        return "admin"
                    } else if (/testplay/.test(importPath)) {
                        return "testplay"
                    }
                }
            }
        }
    });
}()
