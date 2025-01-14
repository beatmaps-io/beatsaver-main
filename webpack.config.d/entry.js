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
                    test: /node_modules[\\/]/,
                    name: "modules",
                    priority: 30,
                    chunks: "initial"
                },
                kotlin: {
                    test: /kotlinx|kotlin-stdlib/,
                    name: "kotlin",
                    priority: 40,
                    chunks: "initial"
                },
                time: {
                    test: /@js-joda|moment/,
                    name: "time",
                    priority: 50,
                    chunks: "initial"
                }
            }
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
                    }
                },
                webpackMode: (modulePath, importPath) => {
                    if (/playlists/.test(importPath)) {
                        return "eager"
                    }
                    return "lazy-once"
                }
            }
        }
    });
}()
