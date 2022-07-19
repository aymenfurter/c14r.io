FROM node:16-alpine AS package
WORKDIR /app
COPY . .


ENV PYTHONUNBUFFERED=1
RUN apk add --update --no-cache python3 && ln -sf python3 /usr/bin/python
RUN python3 -m ensurepip
RUN pip3 install --no-cache --upgrade pip setuptools
RUN apk add --no-cache build-base 
RUN npm ci && npm run build:prod

FROM nginx:stable
COPY --from=package /app/dist /usr/share/nginx/html
EXPOSE 80
