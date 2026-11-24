FROM node:22-alpine
WORKDIR /app
COPY server.mjs package.json ./
COPY public ./public
ENV HOST=0.0.0.0
ENV PORT=5173
EXPOSE 5173
USER node
CMD ["node", "server.mjs"]
