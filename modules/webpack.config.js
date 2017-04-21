var path = require('path')
var webpack = require('webpack')
var HtmlWebpackPlugin = require('html-webpack-plugin')

module.exports = (env = {}) => {

  var webpackConfig = {
    entry: './dm4-webclient/src/main/resources/web/src/main.js',
    output: {
      filename: 'webclient.js',
      path: path.resolve(__dirname, 'dm4-webclient/src/main/resources/web/dist/')
    },
    resolve: {
      alias: {
        modules: __dirname
      }
    },
    module: {
      rules: [
        {
          test: /\.vue$/,
          loader: 'vue-loader'
        },
        {
          test: /\.js$/,
          loader: 'babel-loader',
          exclude: /node_modules/
        },
        {
          test: /\.css$/,
          loader: ['style-loader', 'css-loader']
        },
        {
          test: /\.(png|jpg|jpeg|gif|eot|ttf|woff|woff2|svg|svgz)(\?.+)?$/,
          loader: 'file-loader'
        }
      ]
    },
    plugins: [
      new webpack.DefinePlugin({
        DEV: env.dev,
      }),
      new HtmlWebpackPlugin({
        template: 'dm4-webclient/src/main/resources/web/index.html'
      })
    ]
  }

  if (env.dev) {
    webpackConfig.devServer = {
      port: 8082,
      proxy: [
        {
          context: ['/core'],
          target: 'http://localhost:8080'
        }
      ],
      open: true
    }
  }

  return webpackConfig
}
